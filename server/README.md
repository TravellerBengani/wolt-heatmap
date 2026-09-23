# server

Thin aggregation API. Node + TypeScript + Express + Postgres.

## Local setup (once implemented)

```
cp .env.example .env       # point DATABASE_URL at a local Postgres
npm install
npm run migrate            # applies src/db/schema.sql
npm run dev
```

## Endpoints

- `POST /v1/stops/batch` — clients upload pre-reduced, anonymized stop
  events. See `src/routes/stops.ts` for the TODO contract.
- `GET /v1/heatmap` — clients query current suggestions for a
  city/vehicle-class/day/hour. See `src/routes/heatmap.ts`.

Neither endpoint should ever see raw GPS coordinates or anything that
identifies a specific user/device.
