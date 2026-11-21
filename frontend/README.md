# EV Charging Station Finder frontend

## Run locally

1. Start MySQL with the existing `ev_charging_db` database.
2. Start the Spring Boot backend from `backend/ev-charging-backend` on port 8080.
3. From this directory, install dependencies with `npm install` if needed, then run `npm run dev`.
4. Open the URL printed by Vite (normally http://localhost:5173).

The frontend uses `VITE_API_BASE_URL` as the API root, including `/api` (for example, `http://localhost:8080/api`). If unset, it defaults to `http://localhost:8080/api`. The notification WebSocket URL is derived from this value and uses `wss` when the API URL uses HTTPS.

The public station catalogue is available without signing in at `/public-stations`.

For production, set `VITE_API_BASE_URL` to the deployed API root before building with `npm run build`. Do not place credentials or private keys in Vite variables; values are included in the browser bundle.

Run `npm run lint` and `npm run build` to check the frontend.
