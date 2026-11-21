"""Generate a read-only duplicate-candidate report from the Compose MySQL database."""

import json
import re
import subprocess
import sys
import unicodedata
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
REPORT_DIR = ROOT / "backend" / "ev-charging-backend" / "data" / "audit-reports"
REPORT_STAMP = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
JSON_REPORT = REPORT_DIR / f"duplicate_candidate_audit_report_{REPORT_STAMP}.json"
MARKDOWN_REPORT = REPORT_DIR / f"duplicate_candidate_audit_report_{REPORT_STAMP}.md"
COMPOSE_ARGS = [
    "docker",
    "compose",
    "-f",
    str(ROOT / "docker-compose.yml"),
    "-f",
    str(ROOT / "docker-compose.dev.yml"),
]
MYSQL_COMMAND = (
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 '
    '--user="$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --raw '
    "--skip-column-names --default-character-set=utf8mb4"
)

QUERY = """
START TRANSACTION READ ONLY;
SELECT CONCAT('DATABASE:', DATABASE());
SELECT JSON_OBJECT(
    'id', id,
    'sourceRecordId', source_record_id,
    'sourcePdfRecordId', source_pdf_record_id,
    'sourcePage', source_page,
    'importBatchId', import_batch_id,
    'operator', cpo_name,
    'ownership', ownership,
    'state', state,
    'district', district,
    'cityVillage', city_village,
    'location', location,
    'latitude', latitude,
    'longitude', longitude,
    'chargerType', charger_type,
    'ratingKw', rating_kw,
    'secondaryRatingKw', secondary_rating_kw,
    'connectorCount', connector_count
)
FROM public_charging_stations
ORDER BY id;
COMMIT;
"""


def fetch_live_records():
    command = COMPOSE_ARGS + ["exec", "-T", "mysql", "sh", "-c", MYSQL_COMMAND]
    result = subprocess.run(
        command,
        input=QUERY,
        text=True,
        encoding="utf-8",
        capture_output=True,
        check=True,
        cwd=ROOT,
    )
    lines = [line for line in result.stdout.splitlines() if line.strip()]
    if not lines or not lines[0].startswith("DATABASE:"):
        raise RuntimeError("The live database audit did not return its database name.")
    database_name = lines[0].partition(":")[2]
    return database_name, [json.loads(line) for line in lines[1:]]


def normalize(value):
    if value is None:
        return ""
    text = unicodedata.normalize("NFKC", str(value)).casefold()
    return re.sub(r"\s+", " ", re.sub(r"[^\w]+", " ", text)).strip()


def normalized_identity(record):
    return (
        normalize(record["operator"]),
        normalize(record["ownership"]),
        normalize(record["state"]),
        normalize(record["district"]),
        normalize(record["cityVillage"]),
    )


def record_reference(record):
    return {
        "id": record["id"],
        "sourceRecordId": record["sourceRecordId"],
        "sourcePdfRecordId": record["sourcePdfRecordId"],
        "sourcePage": record["sourcePage"],
        "importBatchId": record["importBatchId"],
        "operator": record["operator"],
        "ownership": record["ownership"],
        "state": record["state"],
        "district": record["district"],
        "cityVillage": record["cityVillage"],
        "location": record["location"],
        "latitude": record["latitude"],
        "longitude": record["longitude"],
        "chargerType": record["chargerType"],
        "ratingKw": record["ratingKw"],
        "connectorCount": record["connectorCount"],
    }


def identity_fields(identity):
    return {
        "operator": identity[0],
        "ownership": identity[1],
        "state": identity[2],
        "district": identity[3],
        "cityVillage": identity[4],
    }


def grouped_candidates(records, key_function, describe_key):
    groups = defaultdict(list)
    for record in records:
        key = key_function(record)
        if key is not None:
            groups[key].append(record)
    return [
        {
            "candidateKey": describe_key(key),
            "candidateCount": len(group),
            "recordIds": [record["id"] for record in group],
        }
        for key, group in groups.items()
        if len(group) > 1
    ]


def source_scope(record):
    batch = (record["importBatchId"] or "").strip()
    return f"batch:{batch}" if batch else "unbatched"


def source_identifier_analysis(records, field_name):
    identifiers = defaultdict(lambda: defaultdict(list))
    for record in records:
        identifier = record[field_name]
        if identifier is not None:
            identifiers[str(identifier)][source_scope(record)].append(record)

    cross_scope = []
    same_scope_duplicates = []
    for identifier, scopes in identifiers.items():
        if len(scopes) > 1:
            cross_scope.append({
                field_name: identifier,
                "sourceScopes": [
                    {
                        "scope": scope,
                        "recordIds": [record["id"] for record in scope_records],
                    }
                    for scope, scope_records in sorted(scopes.items())
                ],
            })
        for scope, scope_records in scopes.items():
            if len(scope_records) > 1:
                same_scope_duplicates.append({
                    field_name: identifier,
                    "sourceScope": scope,
                    "recordIds": [record["id"] for record in scope_records],
                })
    return cross_scope, same_scope_duplicates


def create_report(records, database_name):
    source_record_id_cross_scope, source_record_id_same_scope = source_identifier_analysis(
        records, "sourceRecordId")
    pdf_record_id_cross_scope, pdf_record_id_same_scope = source_identifier_analysis(
        records, "sourcePdfRecordId")

    def address_key(record):
        address = normalize(record["location"])
        identity = normalized_identity(record)
        if not address or not any(identity):
            return None
        return identity, address

    def coordinate_key(record):
        identity = normalized_identity(record)
        latitude = record["latitude"]
        longitude = record["longitude"]
        if not any(identity) or latitude is None or longitude is None:
            return None
        latitude = float(latitude)
        longitude = float(longitude)
        if not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
            return None
        return identity, round(latitude, 4), round(longitude, 4)

    def exact_station_key(record):
        address = normalize(record["location"])
        identity = normalized_identity(record)
        latitude = record["latitude"]
        longitude = record["longitude"]
        if not address or not any(identity) or latitude is None or longitude is None:
            return None
        return (
            identity,
            address,
            round(float(latitude), 6),
            round(float(longitude), 6),
            normalize(record["chargerType"]),
            normalize(record["ratingKw"]),
            normalize(record["secondaryRatingKw"]),
            record["connectorCount"],
        )

    address_candidates = grouped_candidates(
        records,
        address_key,
        lambda key: {
            "stationIdentity": identity_fields(key[0]),
            "normalizedAddress": key[1],
        },
    )
    coordinate_candidates = grouped_candidates(
        records,
        coordinate_key,
        lambda key: {
            "stationIdentity": identity_fields(key[0]),
            "roundedLatitude": key[1],
            "roundedLongitude": key[2],
        },
    )
    exact_station_candidates = grouped_candidates(
        records,
        exact_station_key,
        lambda key: {
            "stationIdentity": identity_fields(key[0]),
            "normalizedAddress": key[1],
            "latitude": key[2],
            "longitude": key[3],
            "chargerType": key[4],
            "ratingKw": key[5],
            "secondaryRatingKw": key[6],
            "connectorCount": key[7],
        },
    )
    candidate_ids = {
        record_id
        for groups in (
            source_record_id_cross_scope,
            source_record_id_same_scope,
            pdf_record_id_cross_scope,
            pdf_record_id_same_scope,
        )
        for group in groups
        for scope in group.get("sourceScopes", [{"recordIds": group.get("recordIds", [])}])
        for record_id in scope["recordIds"]
    }
    for groups in (address_candidates, coordinate_candidates, exact_station_candidates):
        for group in groups:
            candidate_ids.update(group["recordIds"])
    candidate_records = {
        str(record["id"]): {
            key: value
            for key, value in record_reference(record).items()
            if key != "id"
        }
        for record in records
        if record["id"] in candidate_ids
    }

    return {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "database": database_name,
        "table": "public_charging_stations",
        "readOnly": True,
        "recordsAnalyzed": len(records),
        "candidateRules": {
            "sourceRecordId": "A source ID appearing in more than one import scope is reported as an ID collision, not a station duplicate.",
            "normalizedAddress": "NFKC/case-folded address punctuation and whitespace normalization, grouped by operator, ownership, state, district, and city.",
            "coordinates": "Coordinates rounded to 4 decimal places and grouped by the same normalized station identity; proximity is not proof of duplication.",
            "exactStationIdentity": "Normalized station identity and address, six-decimal coordinates, charger type, ratings, and connector count.",
        },
        "summary": {
            "sourceRecordIdCrossScopeGroups": len(source_record_id_cross_scope),
            "sourceRecordIdSameScopeDuplicateGroups": len(source_record_id_same_scope),
            "sourcePdfRecordIdCrossScopeGroups": len(pdf_record_id_cross_scope),
            "sourcePdfRecordIdSameScopeDuplicateGroups": len(pdf_record_id_same_scope),
            "normalizedAddressCandidateGroups": len(address_candidates),
            "normalizedAddressCandidateRecords": sum(group["candidateCount"] for group in address_candidates),
            "roundedCoordinateCandidateGroups": len(coordinate_candidates),
            "roundedCoordinateCandidateRecords": sum(group["candidateCount"] for group in coordinate_candidates),
            "exactStationIdentityCandidateGroups": len(exact_station_candidates),
            "exactStationIdentityCandidateRecords": sum(group["candidateCount"] for group in exact_station_candidates),
        },
        "sourceRecordIdCrossScopeGroups": source_record_id_cross_scope,
        "sourceRecordIdSameScopeDuplicateGroups": source_record_id_same_scope,
        "sourcePdfRecordIdCrossScopeGroups": pdf_record_id_cross_scope,
        "sourcePdfRecordIdSameScopeDuplicateGroups": pdf_record_id_same_scope,
        "candidateRecordsByDatabaseId": candidate_records,
        "normalizedAddressCandidates": address_candidates,
        "roundedCoordinateCandidates": coordinate_candidates,
        "exactStationIdentityCandidates": exact_station_candidates,
        "reviewNotice": "Candidates are evidence for manual review only. This report does not modify, delete, or merge any database records.",
    }


def markdown_report(report):
    summary = report["summary"]
    lines = [
        "# Public Station Duplicate-Candidate Audit",
        "",
        f"- Generated (UTC): {report['generatedAt']}",
        f"- Database source: {report['database']}",
        f"- Table: `{report['table']}`",
        f"- Records analyzed: **{report['recordsAnalyzed']:,}**",
        "- Database access: read-only transaction",
        "",
        "> Candidate matches are not duplicate determinations. No rows were changed, deleted, or merged.",
        "",
        "## Candidate totals",
        "",
        "| Evidence | Candidate groups | Records in groups |",
        "|---|---:|---:|",
        f"| Source record ID reused across import scopes | {summary['sourceRecordIdCrossScopeGroups']:,} | — |",
        f"| Duplicate source record IDs within one scope | {summary['sourceRecordIdSameScopeDuplicateGroups']:,} | — |",
        f"| PDF record ID reused across import scopes | {summary['sourcePdfRecordIdCrossScopeGroups']:,} | — |",
        f"| Duplicate PDF record IDs within one scope | {summary['sourcePdfRecordIdSameScopeDuplicateGroups']:,} | — |",
        f"| Normalized address + station identity | {summary['normalizedAddressCandidateGroups']:,} | {summary['normalizedAddressCandidateRecords']:,} |",
        f"| Coordinates rounded to 4 decimals + station identity | {summary['roundedCoordinateCandidateGroups']:,} | {summary['roundedCoordinateCandidateRecords']:,} |",
        f"| Exact station identity and equipment | {summary['exactStationIdentityCandidateGroups']:,} | {summary['exactStationIdentityCandidateRecords']:,} |",
        "",
        "## Matching rules",
        "",
        "- Source IDs are scoped by `import_batch_id`; re-use across scopes is recorded as a provenance collision, not a station match.",
        "- Addresses use Unicode NFKC normalization, case folding, punctuation removal, and whitespace normalization.",
        "- Address and coordinate matches also require matching normalized operator, ownership, state, district, and city identity.",
        "- Coordinate candidates use 4-decimal rounding and are intentionally broad; nearby records can be separate stations.",
        "- Full record references and candidate groups are in the adjacent JSON report.",
        "",
        "No database rows were updated or removed. Approval and source-level review are required before any proposed merge or deletion.",
        "",
    ]
    return "\n".join(lines)


def main():
    try:
        database_name, records = fetch_live_records()
        report = create_report(records, database_name)
        REPORT_DIR.mkdir(parents=True, exist_ok=True)
        JSON_REPORT.write_text(
            json.dumps(report, ensure_ascii=False, separators=(",", ":")) + "\n",
            encoding="utf-8",
        )
        MARKDOWN_REPORT.write_text(markdown_report(report), encoding="utf-8")
    except (OSError, RuntimeError, subprocess.CalledProcessError, json.JSONDecodeError) as error:
        print(f"Duplicate audit failed: {error}", file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError) and error.stderr:
            print(error.stderr.strip(), file=sys.stderr)
        return 1

    print(f"Analyzed {report['recordsAnalyzed']:,} records from the live Compose database.")
    print(f"JSON report: {JSON_REPORT}")
    print(f"Markdown report: {MARKDOWN_REPORT}")
    print(json.dumps(report["summary"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
