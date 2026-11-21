"""Traceable, retryable import of all parsed EV station PDF records into MySQL.

Inputs are the complete clean and review CSVs produced from every data page,
plus the page-wise extraction validation report. Raw text stays intact in the
staging tables; numeric fields that cannot fit the production schema are NULL
in the catalogue and preserved verbatim in staging.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path


FIELDS = [
    "record_id", "page_number", "cpo_name", "ownership", "state", "district",
    "city_village", "location", "latitude", "longitude", "charger_type",
    "rating_kw", "secondary_rating_kw", "connector_count", "warnings",
]


def sql_string(value: str) -> str:
    escaped = (value.replace("\\", "\\\\").replace("'", "\\'")
               .replace("\0", "\\0").replace("\n", "\\n")
               .replace("\r", "\\r").replace("\x1a", "\\Z"))
    return "'" + escaped + "'"


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"\s*spring\.datasource\.(url|username|password)\s*=\s*(.*)\s*$", line)
        if match:
            values[match.group(1)] = match.group(2)
    return values


def load_rows(paths: list[Path]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    seen: set[int] = set()
    for path in paths:
        with path.open("r", encoding="utf-8-sig", newline="") as source:
            reader = csv.DictReader(source)
            if reader.fieldnames != FIELDS:
                raise ValueError(f"Unexpected CSV schema in {path}: {reader.fieldnames}")
            for row in reader:
                record_id = int(row["record_id"])
                if record_id in seen:
                    raise ValueError(f"Duplicate source record id in input CSVs: {record_id}")
                seen.add(record_id)
                rows.append({key: row[key] or "" for key in FIELDS})
    rows.sort(key=lambda row: int(row["record_id"]))
    if [int(row["record_id"]) for row in rows] != list(range(1, len(rows) + 1)):
        raise ValueError("Source record IDs are not complete and contiguous")
    return rows


def extract_orphans(report: Path) -> list[tuple[int, str]]:
    result: list[tuple[int, str]] = []
    page = 0
    in_orphans = False
    for line in report.read_text(encoding="utf-8").splitlines():
        page_match = re.match(r">>> PAGE (\d+) ", line)
        if page_match:
            page = int(page_match.group(1))
            in_orphans = False
        elif line.startswith("ORPHANED / UNASSIGNED ROWS:"):
            in_orphans = True
        elif in_orphans and line.startswith("  [!] "):
            result.append((page, line[6:]))
        elif in_orphans and not line.strip():
            in_orphans = False
    return result


def mysql_command(sql_file: Path, host: str, port: str, user: str, schema: str, password: str) -> None:
    env = os.environ.copy()
    env["MYSQL_PWD"] = password
    command = [
        "mysql.exe", "--batch", "--default-character-set=utf8mb4",
        f"--host={host}", f"--port={port}", f"--user={user}", schema,
        "--execute", f"source {sql_file.as_posix()}",
    ]
    subprocess.run(command, env=env, check=True)


def mysql_counts(host: str, port: str, user: str, schema: str,
                 password: str, batch_id: str) -> dict[str, int]:
    env = os.environ.copy()
    env["MYSQL_PWD"] = password
    query = f"""
SELECT
 (SELECT COUNT(*) FROM public_charging_stations),
 (SELECT COUNT(*) FROM public_charging_stations WHERE import_batch_id='{batch_id}'),
 (SELECT COUNT(*) FROM public_station_pdf_import_raw WHERE batch_id='{batch_id}' AND record_kind='station'),
 (SELECT COUNT(*) FROM public_station_pdf_import_raw WHERE batch_id='{batch_id}' AND record_kind='station' AND warnings<>''),
 (SELECT COUNT(*) FROM public_stations_pdf_import_orphans WHERE batch_id='{batch_id}'),
 (SELECT COUNT(*) FROM public_charging_stations WHERE import_batch_id='{batch_id}' AND source_warnings IS NOT NULL)
"""
    result = subprocess.run(
        ["mysql.exe", "--batch", "--raw", "--skip-column-names",
         "--default-character-set=utf8mb4", f"--host={host}", f"--port={port}",
         f"--user={user}", schema, "--execute", query],
        env=env, check=True, text=True, capture_output=True,
    )
    values = [int(value) for value in result.stdout.strip().split("\t")]
    names = ["productionTotal", "productionRowsFromBatch", "rawStationRows",
             "rawStationRowsWithWarnings", "rawOrphanRows", "productionRowsWithWarnings"]
    return dict(zip(names, values, strict=True))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--downloads", type=Path, default=Path.home() / "Downloads")
    parser.add_argument("--extract-dir", type=Path, default=Path(__file__).resolve().parents[1] / "data" / "pdf-import")
    parser.add_argument("--backup", type=Path, required=True)
    parser.add_argument("--report-out", type=Path, default=Path(__file__).resolve().parents[1] / "data" / "import-reports")
    args = parser.parse_args()

    pdf = args.downloads / "Ev_charging_station_data.pdf"
    clean_csv = args.extract_dir / "stations_clean.csv"
    review_csv = args.extract_dir / "stations_review.csv"
    validation = args.extract_dir / "station_grouping_full_validation.txt"
    properties = args.project / "src" / "main" / "resources" / "application.properties"
    for path in (pdf, clean_csv, review_csv, validation, properties, args.backup):
        if not path.is_file():
            raise FileNotFoundError(path)
    if args.backup.stat().st_size < 1_000_000 or b"Dump completed" not in args.backup.read_bytes()[-4096:]:
        raise ValueError("The required verified pre-import database backup is missing or incomplete")

    pdf_hash = hashlib.sha256(pdf.read_bytes()).hexdigest()
    rows = load_rows([clean_csv, review_csv])
    orphans = extract_orphans(validation)
    report_text = validation.read_text(encoding="utf-8")
    candidate_match = re.search(r"Candidate Records\s*:\s*(\d+)", report_text)
    pages_match = re.search(r"Pages Evaluated\s*:\s*(\d+) to (\d+)", report_text)
    warnings_match = re.search(r"Records with Warnings\s*:\s*(\d+)", report_text)
    warning_count_match = re.search(r"Total Warning Messages\s*:\s*(\d+)", report_text)
    if not all((candidate_match, pages_match, warnings_match, warning_count_match)):
        raise ValueError("The extraction report lacks its expected summary fields")
    if int(candidate_match.group(1)) != len(rows):
        raise ValueError("CSV rows do not match the full extraction report")
    first_page, last_page = map(int, pages_match.groups())
    if (first_page, last_page) != (1, 1159):
        raise ValueError(f"Unexpected extraction page range: {first_page}-{last_page}")

    args.report_out.mkdir(parents=True, exist_ok=True)
    orphan_csv = args.report_out / f"orphan_rows_{pdf_hash[:12]}.csv"
    with orphan_csv.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(["orphan_ordinal", "source_page", "raw_source_text", "failure_reason"])
        for ordinal, (source_page, raw_text) in enumerate(orphans, 1):
            writer.writerow([ordinal, source_page, raw_text, "PDF row could not be assigned to a station candidate"])

    conn = read_properties(properties)
    url = re.fullmatch(r"jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)(?:\?.*)?", conn.get("url", ""))
    if not url or not conn.get("username") or not conn.get("password"):
        raise ValueError("Datasource settings could not be parsed")
    host, port, schema = url.group(1), url.group(2) or "3306", url.group(3)

    batch = pdf_hash
    backup_hash = hashlib.sha256(args.backup.read_bytes()).hexdigest()
    sql_path = args.report_out / f"import_{pdf_hash[:12]}.sql"
    raw_columns = ",".join("`" + field + "`" for field in FIELDS)
    stage_sql: list[str] = []
    raw_insert_columns = [
        "batch_id", "record_kind", "source_record_id", "source_page",
        "cpo_name", "ownership", "state", "district", "city_village", "location",
        "latitude", "longitude", "charger_type", "rating_kw", "secondary_rating_kw",
        "connector_count", "warnings", "raw_source_text", "failure_reason",
    ]
    raw_values: list[str] = []
    for row in rows:
        values = [sql_string(batch), sql_string("station"), str(int(row["record_id"])),
                  str(int(row["page_number"]))]
        values.extend(sql_string(row[field]) for field in FIELDS[2:])
        values.extend([sql_string(json.dumps(row, ensure_ascii=False)),
                       sql_string(row["warnings"]) if row["warnings"] else "NULL"])
        raw_values.append("(" + ",".join(values) + ")")
    for offset in range(0, len(raw_values), 250):
        stage_sql.append(
            "INSERT INTO public_station_pdf_import_raw ("
            + ",".join(f"`{column}`" for column in raw_insert_columns)
            + ") VALUES\n" + ",\n".join(raw_values[offset:offset + 250])
            + " ON DUPLICATE KEY UPDATE raw_id=public_station_pdf_import_raw.raw_id;"
        )
    orphan_values = [
        "(" + ",".join([
            sql_string(batch), str(ordinal), str(source_page), sql_string(raw_text),
            sql_string("PDF row could not be assigned to a station candidate"),
        ]) + ")"
        for ordinal, (source_page, raw_text) in enumerate(orphans, 1)
    ]
    for offset in range(0, len(orphan_values), 250):
        stage_sql.append(
            "INSERT INTO public_stations_pdf_import_orphans "
            "(batch_id,orphan_ordinal,source_page,raw_source_text,failure_reason) VALUES\n"
            + ",\n".join(orphan_values[offset:offset + 250])
            + " ON DUPLICATE KEY UPDATE orphan_id=public_stations_pdf_import_orphans.orphan_id;"
        )
    stage_sql_text = "\n".join(stage_sql)
    sql = f"""
CREATE TABLE IF NOT EXISTS public_station_pdf_import_batches (
  batch_id CHAR(64) NOT NULL PRIMARY KEY,
  source_pdf_name VARCHAR(255) NOT NULL,
  source_pdf_sha256 CHAR(64) NOT NULL UNIQUE,
  source_report_sha256 CHAR(64) NOT NULL,
  backup_sha256 CHAR(64) NOT NULL,
  source_pages INT NOT NULL,
  records_extracted INT NOT NULL,
  records_with_warnings INT NOT NULL,
  warning_messages INT NOT NULL,
  orphan_rows INT NOT NULL,
  production_rows_inserted INT NOT NULL DEFAULT 0,
  import_status VARCHAR(24) NOT NULL,
  imported_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS public_station_pdf_import_raw (
  raw_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  batch_id CHAR(64) NOT NULL,
  record_kind VARCHAR(16) NOT NULL,
  source_record_id INT NULL,
  source_page INT NULL,
  cpo_name LONGTEXT, ownership LONGTEXT, state LONGTEXT, district LONGTEXT,
  city_village LONGTEXT, location LONGTEXT, latitude LONGTEXT, longitude LONGTEXT,
  charger_type LONGTEXT, rating_kw LONGTEXT, secondary_rating_kw LONGTEXT,
  connector_count LONGTEXT, warnings LONGTEXT,
  raw_source_text LONGTEXT, failure_reason LONGTEXT,
  production_id BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_pdf_batch_station (batch_id, record_kind, source_record_id),
  KEY idx_pdf_raw_page (batch_id, source_page),
  KEY idx_pdf_raw_state (batch_id, state(80)),
  KEY idx_pdf_raw_city (batch_id, city_village(80))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS public_stations_pdf_import_orphans (
  orphan_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  batch_id CHAR(64) NOT NULL,
  orphan_ordinal INT NOT NULL,
  source_page INT NOT NULL,
  raw_source_text LONGTEXT NOT NULL,
  failure_reason LONGTEXT NOT NULL,
  UNIQUE KEY uq_pdf_orphan (batch_id, orphan_ordinal),
  KEY idx_pdf_orphan_page (batch_id, source_page)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE public_charging_stations
  MODIFY COLUMN latitude DECIMAL(12,6) NULL,
  MODIFY COLUMN longitude DECIMAL(12,6) NULL,
  MODIFY COLUMN secondary_rating_kw VARCHAR(255) NULL;

DELIMITER $$
DROP PROCEDURE IF EXISTS ensure_pdf_import_columns$$
CREATE PROCEDURE ensure_pdf_import_columns()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='public_charging_stations'
        AND COLUMN_NAME='import_batch_id') THEN
    ALTER TABLE public_charging_stations ADD COLUMN import_batch_id CHAR(64) NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='public_charging_stations'
        AND COLUMN_NAME='source_pdf_record_id') THEN
    ALTER TABLE public_charging_stations ADD COLUMN source_pdf_record_id INT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='public_charging_stations'
        AND COLUMN_NAME='source_warnings') THEN
    ALTER TABLE public_charging_stations ADD COLUMN source_warnings LONGTEXT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='public_charging_stations'
        AND INDEX_NAME='uq_public_station_pdf_import') THEN
    CREATE UNIQUE INDEX uq_public_station_pdf_import
      ON public_charging_stations (import_batch_id, source_pdf_record_id);
  END IF;
END$$
CALL ensure_pdf_import_columns()$$
DROP PROCEDURE ensure_pdf_import_columns$$
DELIMITER ;

INSERT INTO public_station_pdf_import_batches (
 batch_id,source_pdf_name,source_pdf_sha256,source_report_sha256,backup_sha256,
 source_pages,records_extracted,records_with_warnings,warning_messages,orphan_rows,import_status
) VALUES (
 {sql_string(batch)},'Ev_charging_station_data.pdf',{sql_string(pdf_hash)},
 {sql_string(hashlib.sha256(validation.read_bytes()).hexdigest())},{sql_string(backup_hash)},
 1159,{len(rows)},{int(warnings_match.group(1))},{int(warning_count_match.group(1))},
 {len(orphans)},'LOADING'
) ON DUPLICATE KEY UPDATE batch_id=batch_id;

{stage_sql_text}

DELIMITER $$
DROP PROCEDURE IF EXISTS import_pdf_station_batch$$
CREATE PROCEDURE import_pdf_station_batch()
BEGIN
  DECLARE done_count INT DEFAULT 0;
  DECLARE actual_raw INT DEFAULT 0;
  SELECT COUNT(*) INTO actual_raw FROM public_station_pdf_import_raw
    WHERE batch_id={sql_string(batch)} AND record_kind='station';
  SELECT import_status INTO @batch_status FROM public_station_pdf_import_batches
    WHERE batch_id={sql_string(batch)} FOR UPDATE;
  IF actual_raw <> {len(rows)} THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Raw PDF staging row count does not match extracted records';
  END IF;
  IF @batch_status <> 'COMPLETE' THEN
    START TRANSACTION;
    INSERT INTO public_charging_stations (
      source_record_id,source_page,cpo_name,ownership,state,district,city_village,location,
      latitude,longitude,charger_type,rating_kw,secondary_rating_kw,connector_count,
      import_batch_id,source_pdf_record_id,source_warnings
    )
    SELECT source_record_id,source_page,LEFT(cpo_name,255),LEFT(ownership,255),LEFT(state,255),
      LEFT(district,255),LEFT(city_village,255),location,
      CASE WHEN TRIM(latitude) REGEXP '^[+-]?[0-9]+(\\.[0-9]+)?$' THEN CAST(TRIM(latitude) AS DECIMAL(12,6)) ELSE NULL END,
      CASE WHEN TRIM(longitude) REGEXP '^[+-]?[0-9]+(\\.[0-9]+)?$' THEN CAST(TRIM(longitude) AS DECIMAL(12,6)) ELSE NULL END,
      LEFT(charger_type,255),
      CASE WHEN TRIM(rating_kw) REGEXP '^[+-]?[0-9]+(\\.[0-9]+)?$' THEN CAST(TRIM(rating_kw) AS DECIMAL(38,2)) ELSE NULL END,
      NULLIF(LEFT(secondary_rating_kw,255),''),
      CASE WHEN TRIM(connector_count) REGEXP '^[0-9]+$' THEN CAST(TRIM(connector_count) AS UNSIGNED) ELSE NULL END,
      batch_id,source_record_id,NULLIF(warnings,'')
    FROM public_station_pdf_import_raw
    WHERE batch_id={sql_string(batch)} AND record_kind='station'
    ORDER BY source_record_id
    ON DUPLICATE KEY UPDATE id=public_charging_stations.id;
    UPDATE public_station_pdf_import_raw r
      JOIN public_charging_stations p
        ON p.import_batch_id=r.batch_id AND p.source_pdf_record_id=r.source_record_id
      SET r.production_id=p.id
      WHERE r.batch_id={sql_string(batch)} AND r.record_kind='station';
    UPDATE public_station_pdf_import_batches
      SET production_rows_inserted=(SELECT COUNT(*) FROM public_charging_stations WHERE import_batch_id={sql_string(batch)}),
          import_status='COMPLETE', imported_at=CURRENT_TIMESTAMP
      WHERE batch_id={sql_string(batch)};
    COMMIT;
  END IF;
END$$
CALL import_pdf_station_batch()$$
DROP PROCEDURE import_pdf_station_batch$$
DELIMITER ;

SELECT batch_id,source_pages,records_extracted,records_with_warnings,warning_messages,
       orphan_rows,production_rows_inserted,import_status
FROM public_station_pdf_import_batches WHERE batch_id={sql_string(batch)};
SELECT COUNT(*) AS raw_station_rows FROM public_station_pdf_import_raw
 WHERE batch_id={sql_string(batch)} AND record_kind='station';
SELECT COUNT(*) AS raw_orphan_rows FROM public_stations_pdf_import_orphans
 WHERE batch_id={sql_string(batch)};
"""
    sql_path.write_text(sql, encoding="utf-8")
    mysql_command(sql_path, host, port, conn["username"], schema, conn["password"])

    actual = mysql_counts(host, port, conn["username"], schema, conn["password"], batch)
    expected = {
        "productionRowsFromBatch": len(rows),
        "rawStationRows": len(rows),
        "rawStationRowsWithWarnings": int(warnings_match.group(1)),
        "rawOrphanRows": len(orphans),
        "productionRowsWithWarnings": int(warnings_match.group(1)),
    }
    mismatches = {key: (expected_value, actual[key])
                  for key, expected_value in expected.items()
                  if expected_value != actual[key]}
    if mismatches:
        raise RuntimeError(f"Database import verification mismatch: {mismatches}")

    report = {
        "sourcePdf": pdf.name,
        "sourcePdfSha256": pdf_hash,
        "validationReportSha256": hashlib.sha256(validation.read_bytes()).hexdigest(),
        "pagesInPdf": 1159,
        "pagesProcessed": 1159,
        "recordsExtracted": len(rows),
        "cleanRecords": sum(not row["warnings"].strip() for row in rows),
        "recordsWithWarnings": int(warnings_match.group(1)),
        "warningMessages": int(warning_count_match.group(1)),
        "orphanRowsRetained": len(orphans),
        "databaseCounts": {
            **actual,
            "existingRowsOutsideThisBatch": actual["productionTotal"] - actual["productionRowsFromBatch"],
            "rawRowsRetainedTotal": actual["rawStationRows"] + actual["rawOrphanRows"],
        },
        "orphanCsv": str(orphan_csv),
        "backup": str(args.backup.resolve()),
        "backupSha256": backup_hash,
        "completedAtUtc": datetime.now(timezone.utc).isoformat(),
        "importSql": str(sql_path),
    }
    out = args.report_out / f"pdf_import_report_{pdf_hash[:12]}.json"
    out.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"report": str(out), **report}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Import halted: {exc}", file=sys.stderr)
        raise
