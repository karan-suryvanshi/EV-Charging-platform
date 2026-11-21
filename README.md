# EV Charging Station Finder

Local React and Spring Boot application for managed charging stations and the India public station directory. The authenticated Charging Stations area includes both bookable managed stations and the paginated public catalogue; `/public-stations` remains available for guest browsing. The frontend does not download the full catalogue at once. Booking, account and admin features remain part of the existing application.

## Current verified data snapshot

The completed PDF import report is `backend/ev-charging-backend/data/import-reports/pdf_import_report_f519218fa96a.json`. It records **1,159 of 1,159 pages processed**, **38,332 source station rows extracted**, **38,332 rows inserted into the production station table**, **9,771 rows carrying parsing warnings**, and **186 orphan text rows retained in a separate raw table**. On 2026-10-26, direct read-only queries reconfirmed **66,755 production rows** (28,423 pre-existing plus 38,332 from the batch), **38,332 raw station rows**, and **186 orphan rows**. The batch table reports `COMPLETE`, 1,159 pages, 38,332 extracted and inserted, 9,771 warning rows. The import is traceable and idempotent. No duplicate candidates were removed.

The PDF parser extracted selectable text; it did not run OCR. Orphan rows and warning rows remain available for review. See the batch report and `data/pdf-import/station_grouping_full_validation.txt` for extraction details. No separate connector-type field was present in the production schema.

## Folders

- `backend/ev-charging-backend`: Spring Boot API, entities, repositories, tests and PDF import utilities.
- `frontend`: React/Vite application and public catalogue route (`/public-stations`).
- `backups`: pre-import SQL backup; SQL dumps are ignored by Git.
- `ocpp-simulator`: existing simulator project.

## Local prerequisites

- Java 17 (the project targets Java 17).
- MySQL 8 with the existing `ev_charging_db` database, if you need the imported catalogue.
- Node.js compatible with the installed Vite release and npm.
- Docker Desktop with Compose v2.24+ for the optional container workflow (the dev override uses Compose's `!override` merge tag).

The backend reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` and `CORS_ALLOWED_ORIGINS` from the environment. Copy `.env.example` to `.env`, replace every placeholder with local-only values, and set the backend variables in the shell that starts Spring. Do not commit `.env`. `VITE_API_BASE_URL` is browser-visible and must not contain secrets.

### Start with local MySQL

In PowerShell, point `JAVA_HOME` to JDK 17, then run the host launcher. It prompts for the actual local MySQL username, masked database password and masked JWT secret, checks the login with read-only `SELECT 1` when `mysql.exe` is available, then exports values only to the backend process and clears them when it exits. Enter the current JWT secret to keep existing tokens valid; using a new one invalidates old tokens. Spring Boot does not load `.env` automatically, and the Compose `.env` credentials are not assumed to work against host MySQL.

```powershell
cd backend/ev-charging-backend
.\scripts\start-local.ps1
```

In another terminal:

```powershell
cd frontend
npm ci
npm run dev -- --host 127.0.0.1
```

Open `http://localhost:5173/public-stations`. The backend API is `http://localhost:8080/api`.

## Docker Compose

The Docker Compose setup uses a persistent MySQL 8 named volume, a Spring Boot backend and a frontend. The volume is persistent and may already contain application data; do not assume it is empty, replace it, or import data into it without approval and a verified backup. At the latest verification, the existing volume contained 66,755 public stations, 0 managed stations, 0 bookings, and 2 users. Starting the services did not import or remove station records. The historical pre-import backup contains the older database state, not the post-import 66,755-row state.

1. Copy `.env.example` to `.env` and set unique local passwords and a strong random `JWT_SECRET`.
2. Start the Compose services:

   ```powershell
   docker compose up -d --build backend frontend
   ```

3. Open `http://localhost:8081/public-stations`. The Docker API is at `http://localhost:8082/api`.

The base Compose frontend is at `http://localhost:8081`. The optional development override serves it at `http://localhost:5173`; it was verified with the isolated test API during the latest browser flow and produced a Vite HMR update. In normal use, both frontends use the Docker API at `http://localhost:8082/api`. The Docker backend listens on port 8080 inside the Compose network and connects to MySQL at `mysql:3306`. MySQL is bound to localhost port 3307 only. Native Windows Vite remains configured for the existing host Java backend on port 8080.

Common commands (keep the named DB volume):

```powershell
docker compose ps
docker compose logs -f backend
docker compose restart backend
docker compose up -d --build backend frontend
docker compose down
```

Do not use `down -v` for routine shutdown. Frontend source files are bind-mounted and Vite polling is enabled for Windows/Docker file events. React, CSS and Vite configuration changes update/restart the development frontend; package manifest or lockfile changes rebuild its image. Backend source sync restarts the Spring/Maven process so Java is recompiled; `pom.xml` changes rebuild the backend image. Production images do not enable DevTools.

### Security and performance notes

- Configure `CORS_ALLOWED_ORIGINS` as a comma-separated list of exact frontend origins for deployment. REST and WebSocket origin checks use this setting; the defaults are localhost development origins. Terminate HTTPS at a trusted reverse proxy/load balancer in deployments.
- JWTs are bearer tokens stored in browser `localStorage`; an XSS issue could expose a token until its one-hour expiry. The UI currently uses no HttpOnly-cookie session. Login/register rate limiting is also not configured.
- The backend container runs as UID 10001. The Nginx frontend sets `X-Content-Type-Options`, `X-Frame-Options`, and `Referrer-Policy` headers. The backend requires a configured JWT secret and uses BCrypt for passwords. SQL statement logging is disabled.
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update` is still configured by base Compose. This can mutate schema at startup; production should move to reviewed versioned migrations and a non-DDL application DB role before deployment. This audit did not change database schema or grants.
- Search results are paginated (maximum 100 per page) and map responses are capped. A read-only live `EXPLAIN` showed the public station normalized-state predicate cannot use a state index and scans the primary index; the 66,755-row table currently has only its primary index. Booking overlap uses the station foreign-key index, then filters date/status/time. No index migration was applied. Revisit with representative query benchmarks before adding migration-managed composite/normalized indexes; broad substring search will still require a scan or a separately designed search index.

### Backup and restore

The current local MySQL database should be backed up before schema or data changes. A post-import dump was created on 2026-10-26 at `backups/ev_charging_db_post_pdf_import_20261026.sql` (55,939,294 bytes; SHA-256 `73a2767c6f350efa1e01ab1d6549d052eecbf5b8ccd82c34f7ea8a07438f6e13`). It has database/table declarations and a completed-dump footer; a restore test was not run. The pre-import backup remains at `backups/ev_charging_db_pre_pdf_import_20261026.sql` and contains only the older 28,423-row state. SQL dumps are ignored by Git and should be stored securely.

For another PowerShell-safe backup, provide the local DB credentials through the environment and run the binary-streaming helper (it refuses to overwrite an existing path):

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/ev_charging_db'
$env:DB_USERNAME = '<your local MySQL username>'
$env:DB_PASSWORD = '<your local MySQL password>'
python backend/ev-charging-backend/scripts/backup_mysql_database.py backups/ev_charging_db_<timestamp>.sql
```

The helper checks the dump footer and prints its SHA-256. Restore only into a new/empty Compose database. For the verified full-data backup:

```powershell
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql
python backend/ev-charging-backend/scripts/restore_docker_mysql_backup.py backups/ev_charging_db_post_pdf_import_20261026.sql --confirm-empty-db ev_charging_db
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --watch --build
```

The restore helper streams the dump only after checking the named Compose database has zero tables and the confirmation matches the database encoded in the dump. Run it against a new/empty Compose database only. It refuses a nonempty target and never deletes a volume. Do not restore the pre-import dump when the goal is to preserve the completed import; it predates the PDF batch.

## Public station API

- `GET /api/public-stations?page=0&size=20`
- `GET /api/public-stations?q=Pune&page=0&size=20`
- Optional combined filters: `state`, `district`, `city`, `chargerType`, `operator`.
- `GET /api/public-stations/filters` returns database-backed filter options.
- Admin-only `PUT /api/public-stations/{id}` updates editable public station details by the public table's database ID.
- Admin-only `DELETE /api/public-stations/{id}` deletes one public row by database ID and returns a conflict if dependent records prevent deletion.

Page is zero-based; supported size is 1–100. Responses include page content and pagination metadata. Search is case-insensitive and runs in the database before pagination. Directory records are public reference data, not booking-enabled live charging points. Availability and prices may have changed.

## Import trace and resume

The PDF extraction outputs and batch report are under `backend/ev-charging-backend/data`. The importer at `scripts/import_pdf_station_data.py` identifies the source PDF hash and batch, uses source record IDs, writes raw rows and is safe to retry for the same batch. Inspect the existing batch before running it; do not run a new import just to check status. Existing production rows and possible duplicates are preserved.

## Checks

```powershell
cd backend/ev-charging-backend
.\mvnw.cmd test
cd ../../frontend
npm audit
npm run test
npm run lint
npm run build
```

The Maven wrapper needs access to its configured Maven distribution unless already cached. Backend tests use `application-test.properties`, which defaults to a disposable MySQL test database at `127.0.0.1:3310/ev_charging_test` and creates/drops its test schema. Start an isolated test database there or override it with `TEST_DB_URL`, `TEST_DB_USERNAME`, and `TEST_DB_PASSWORD`; do not point this profile at the existing Compose database. Do not interpret wrapper-download failure as a test result.

## Latest verification

Verified 2026-11-02. Live data checks used the existing Compose MySQL volume; automated tests used a separate MySQL test container backed only by tmpfs.

- Backend integration suite: **19 tests, 0 failures, 0 errors, 0 skipped**, against the isolated test database. Coverage includes registration/login, unified discovery, booking overlap and concurrency, cancellation, ownership/authorization, managed- and public-station CRUD isolation, and dashboard endpoints.
- Frontend: **3 Vitest tests passed**; `npm run lint`, `npm run build`, and `npm audit` passed. The initial six moderate findings were all dependency-chain reports around `minimatch` below 11.0.1, affecting `minimatch`, `@eslint/config-array`, `@eslint-community/eslint-utils`, `eslint`, `@eslint/js`, and `eslint-plugin-react-refresh`. A targeted `minimatch` override resolved them without downgrading ESLint; the installed version is 11.0.4 and audit reports zero vulnerabilities. The build now emits separate Login, Register, UnifiedDiscovery, AdminDashboard, and MapView chunks; the largest emitted JS chunk is **312.94 kB**, with no chunk-size or mixed static/dynamic import warning.
- Backend and frontend production images built successfully; `docker compose config --quiet` passed. Both backend and frontend Compose services were rebuilt and recreated for the admin dashboard change. MySQL was not recreated or restarted, and its named volume was retained. All three services reported healthy afterward.
- The Compose MySQL container was restarted without removing its named volume. It returned healthy, and read-only counts were unchanged: **66,755 public stations, 0 managed stations, 0 bookings, 2 users (1 ADMIN and 1 USER), and 0 notifications**. The later CRUD verification used temporary managed QA stations in two sequential browser passes; each test row was deleted, and no pre-existing station, account, booking, or public row was altered.
- The live frontend returned HTTP 200 on `/admin`. The authenticated admin dashboard now displays **66,755 Total Stations**, **66,755 Public Stations**, and **0 Managed Stations**, matching the live public API's `totalElements: 66755` and the managed API's 0 rows. The new `/api/admin/stations/summary` uses database repository counts and is restricted to ADMIN; integration tests verify unauthenticated access returns 401 and USER access returns 403.
- Before the public CRUD change, the live Charging Station Management view rendered **20 of 66,755** records per page and public rows had no Edit/Delete controls. Searching `Guntur` displayed **20 of 131** matches. The unified discovery API returned 20 records on each of pages 0 and 1, with different first records; the managed-only query returned 0.
- Admin managed-station CRUD was exercised in the live Compose UI using explicitly named temporary QA stations in two sequential passes: create returned each station in the Managed filter with refreshed totals, edit updated its name, and delete required a confirmation dialog and removed the same test row. Form validation rejected a latitude without longitude; the station was created after both valid coordinates were supplied. The final API counts returned to **66,755 public and 0 managed**. No pre-existing station, booking, account or public record was changed; both temporary managed QA rows were deleted.
- The integrated CRUD tests confirm USER requests receive 403 for create, update and delete, while ADMIN can perform source-specific operations. Public-row tests exercise update, coordinate-pair validation, search after edit, and deletion of a dedicated QA fixture whose numeric ID collides with a managed fixture; they verify the managed row and a sibling public row remain unchanged. The earlier live public-only inspection showed **20 of 66,755**, availability Unknown, zero Edit buttons and zero Delete buttons.
- The live discovery browser page showed **66,755 stations**. Map view showed **250 mapped stations** and labeled the public directory as reference data. A public marker popup showed availability as **Not verified**; public records are not bookable.
- Existing manual isolated browser checks covered admin and normal-user login, station discovery, search, map rendering, booking, cancellation, and admin user filtering/management. The API integration tests also cover those backend actions. A persistent automated Playwright browser suite was not added: the required recorder child agent was unavailable, so the existing runtime plan uses its HTTP-probe fallback.
- The development Compose override served the UI on port 5173 and its Vite HMR update was observed previously. It was not restarted for this code audit.
- Standalone TypeScript runtime validation: **4/4 assertions passed** (frontend lint, production build, Vitest, and HTTP probe).
- The existing live duplicate-candidate report remains under `backend/ev-charging-backend/data/audit-reports`. Candidates are for review only; no public records were deleted or merged.

These are local verification results, not a production-readiness claim. The current live database has no managed stations or bookings; nonempty managed-station and booking UI flows were tested only in the isolated tmpfs environment. The updated Compose backend uses the existing database volume and is healthy; public-station administration added no schema changes. The source schema still relies on Hibernate `update`, JWTs remain in local storage, authentication rate limiting is absent, and the public search query/index plan needs measured optimization as volume grows.

### Public station admin CRUD deployment (2026-11-02)

- The Compose dashboard now shows Edit and Delete for both source types. Public edits and deletes use the public table's actual `internalId`; managed operations continue to use `/api/stations/{id}`. Public update fields exclude import/source identity, unverified availability, and price. Public deletion is a single-row operation and reports a conflict when the database rejects deletion due to dependent data.
- Backend tests ran against a separate MySQL container backed by tmpfs and passed **19/19**. The public CRUD test proves unauthenticated requests are denied, USER requests are forbidden, ADMIN updates and deletes only the public table row even when its ID collides with a managed row, and sibling fixtures remain intact. No live row was used for CRUD testing.
- `npm run test` passed **3/3**; `npm run lint` and `npm run build` passed. The post-deployment authenticated admin browser view showed 20 public rows with 20 Edit and 20 Delete controls. Searching `Guntur` returned **131** matches; the managed filter showed no rows. API pagination returned distinct first-row IDs for pages 0 and 1. No UI CRUD action was submitted.
- Both updated Docker images built successfully. Compose recreated backend and frontend with `docker compose up -d --no-deps backend frontend`; MySQL was not restarted or recreated. All three services reported healthy afterward.
- The pre-existing post-import backup was readable and its SHA-256 matched the README record: `73a2767c6f350efa1e01ab1d6549d052eecbf5b8ccd82c34f7ea8a07438f6e13` (55,939,294 bytes). The existing `public_charging_stations` schema already contained all mapped columns; no schema change was needed.
- Read-only API/database verification before and after deployment showed **66,755 public stations, 0 managed stations, 0 bookings, and 2 users (1 ADMIN and 1 USER)**. The MySQL container ID and `mysql_data` volume mount were identical before and after. Unauthenticated public-station PUT/DELETE and admin-summary requests returned **401**; isolated integration tests confirmed USER CRUD requests return **403**.
- Deployment is complete. No live station, account, or booking was edited or deleted. The existing Compose database and volume were preserved; the new live admin capability can now modify/delete public rows when used by an authorized ADMIN.

### Admin dashboard analytics deployment (2026-11-02)

- Added the ADMIN-only read endpoint `GET /api/admin/analytics`. It returns database counts for public and managed stations, users by role, and bookings by status; a rolling 12-month booking report; and the top 10 public cities and managed locations. Aggregations run in SQL and do not load the public station catalogue into the frontend.
- Active-user totals are unavailable because users have no reliable status field. Revenue and monthly revenue are explicitly unavailable because the application stores no payment records; booking or station prices are not treated as collected revenue. Managed station data is grouped by its stored `location` string, not inferred into city names.
- Verification against an isolated tmpfs MySQL test database: the analytics integration test passed, including anonymous and USER denial, ADMIN access, persisted aggregate values, month/status totals, and checks that personal account data is absent. The complete backend suite passed **20/20** tests.
- Frontend standalone runtime validation passed **4/4** assertions: lint, production build, Vitest (**3/3**), and an HTTP 200 probe for `/public-stations`.
- Before deployment, the existing post-import backup was fully read and its SHA-256 verified as `73a2767c6f350efa1e01ab1d6549d052eecbf5b8ccd82c34f7ea8a07438f6e13` (55,939,294 bytes). The existing analytics columns were confirmed in MySQL; this change adds no entities or schema migration. The analytics SQL uses database-side counts/grouping and limits place results to 10.
- Both Docker images built successfully. Deployment used `docker compose up -d --no-deps backend frontend`. Backend, frontend, and MySQL reported healthy. MySQL was not restarted or recreated: its container ID and `ev-charging-platform_mysql_data` volume mount were identical before and after deployment.
- Post-deployment live read-only counts were **66,755 public stations, 0 managed stations, 2 users (1 USER, 1 ADMIN), and 0 bookings**. The authenticated ADMIN analytics response returned **66,755 total stations, 66,755 public, 0 managed, 2 total users, 1 normal user, 1 admin, and 0 total/confirmed/cancelled bookings**; each count matched a direct MySQL query. It returned 12 monthly reports. Revenue and active-user availability were both `false`; the UI clearly labels revenue unavailable and explains that no payment records or user status field exist.
- Live API authorization checks returned **200** for ADMIN, **401** unauthenticated, and **403** for USER. The browser dashboard rendered the live totals and unavailable-data labels; the All/Public/Managed filters, Add New Station button, and 20 public-row Edit/Delete controls were visible. Public discovery returned HTTP 200 and displayed the current 66,755 count. No create/edit/delete action was submitted.
- No live station, user, or booking was changed. The temporary browser verification session was cleared. The analytics module is deployed to this existing local Compose environment; this verification is not a production-readiness claim.

### Booking enablement deployment (2026-11-03)

- Added independent `booking_enabled` flags to managed and imported public stations. Both entity defaults are enabled. Admins can toggle stations individually through `PUT /api/admin/stations/{source}/{id}/booking-enabled`; only `ADMIN` is authorized. Admin listing and unified discovery/map APIs support server-side enabled/disabled filtering.
- Managed bookings retain the `station_id` relation. Public booking requests use `/api/bookings/public-station/{id}` and the separate `public_station_id`, so colliding numeric IDs cannot cross-map. Public requests are `REQUESTED`, not confirmed reservations; directory availability remains unverified. Disabling booking does not change actual availability or cancel bookings. Network source labels were removed from the affected UI while import metadata remains stored.
- The reviewed, non-idempotent migration `backend/ev-charging-backend/src/main/resources/db/manual/booking-enablement-and-public-bookings.sql` was applied exactly once after the verified backup and read-only preflight. The release override sets `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` to prevent Hibernate from competing with the manual schema change.
- Backup used for the migration: `backups/ev_charging_db_booking_preflight_20261103_0030_46.sql`, 17,244,054 bytes, SHA-256 `149a4c64ecbd2ef89dc58ba3ef43e803d12cdd9e9b3098fcce981998c5dc88da`. It was restored and count-verified in an isolated tmpfs MySQL container before migration.
- Post-migration verification: both station flags are `BIT(1) NOT NULL DEFAULT b'1'`; all **66,755** public rows have booking enabled. Booking station relations are nullable and source-specific, `REQUESTED` is in the status enum, both slot indexes, the public-station FK, and source check constraint exist. The six baseline counts remained unchanged: **66,755 public stations, 0 managed stations, 0 bookings, 2 users, 0 notifications, 0 reviews**.
- Backend suite passed **22/22** tests against a disposable tmpfs MySQL database; frontend Vitest passed **3/3**, lint passed, and the production build passed. Release backend and frontend images built successfully. All Compose services are healthy; only backend and frontend were recreated using `up -d --no-deps`. MySQL container and `mysql_data` volume remained unchanged.
- Live checks: frontend `http://localhost:8081` returned HTTP 200 and rendered public discovery with 66,755 results and “Availability unverified”; public discovery and map APIs returned HTTP 200, public booking-enabled was true, and the disabled filter returned zero rows. Unauthenticated admin analytics returned HTTP 401. Isolated integration tests verify login, USER denial (403), ADMIN controls for both station sources, disabled-booking denial, public `REQUESTED` status, overlap/cancellation, and source isolation.
- No live booking was created and no station setting was toggled as a deployment test. A successful live ADMIN login/USER session was not exercised; role-specific behavior was verified by isolated integration tests. This is a local Compose deployment, not a production-readiness claim.

### Live map clustering fix (2026-11-03)

- Browser verification of the deployed public map initially exposed a Leaflet runtime error, `Map has no maxZoom specified`, when MarkerCluster initialized. `MapContainer` and its tile layer now declare the same maximum zoom (19), avoiding the initialization-order dependency.
- The map discovery API returns a direct station array and is requested with a 100,000-point limit. The map normalized and loaded **66,754** valid public station locations; the one remaining public record has no usable coordinates and is excluded rather than assigned invented coordinates. The database currently has 0 managed stations.
- In the browser at `http://localhost:8081/public-stations`, switching to Map View rendered the map and displayed **“Showing 66,754 mapped stations”**. At the India-wide zoom, station points were represented by cluster markers plus individual green-dot markers; the cluster loader reported completion for the full valid station list. The map no longer displays “Availability unverified” or “Booking disabled” labels.
- Frontend verification after the fix: `npm run lint` passed, `npm test` passed **6/6**, and `npm run build` passed. The frontend image built successfully and only the frontend service was recreated with `docker compose -f docker-compose.yml -f docker-compose.release.yml up -d --no-deps frontend`.
- After deployment, backend, frontend and MySQL all reported healthy. MySQL retained container ID `d41b95f938e5d85fa0a7a3477b4b700d68ae9e0b0bc1c9f55980b438d5547d8f` and the `ev-charging-platform_mysql_data:/var/lib/mysql` mount; no database or station data was changed.
