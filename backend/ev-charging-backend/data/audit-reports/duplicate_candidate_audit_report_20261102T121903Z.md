# Public Station Duplicate-Candidate Audit

- Generated (UTC): 2026-11-02T12:19:16.991581+00:00
- Database source: ev_charging_db
- Table: `public_charging_stations`
- Records analyzed: **66,755**
- Database access: read-only transaction

> Candidate matches are not duplicate determinations. No rows were changed, deleted, or merged.

## Candidate totals

| Evidence | Candidate groups | Records in groups |
|---|---:|---:|
| Source record ID reused across import scopes | 28,423 | — |
| Duplicate source record IDs within one scope | 0 | — |
| PDF record ID reused across import scopes | 0 | — |
| Duplicate PDF record IDs within one scope | 0 | — |
| Normalized address + station identity | 27,940 | 66,389 |
| Coordinates rounded to 4 decimals + station identity | 5,626 | 22,357 |
| Exact station identity and equipment | 3,057 | 11,444 |

## Matching rules

- Source IDs are scoped by `import_batch_id`; re-use across scopes is recorded as a provenance collision, not a station match.
- Addresses use Unicode NFKC normalization, case folding, punctuation removal, and whitespace normalization.
- Address and coordinate matches also require matching normalized operator, ownership, state, district, and city identity.
- Coordinate candidates use 4-decimal rounding and are intentionally broad; nearby records can be separate stations.
- Full record references and candidate groups are in the adjacent JSON report.

No database rows were updated or removed. Approval and source-level review are required before any proposed merge or deletion.
