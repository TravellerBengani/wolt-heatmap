# CLAUDE.md — Project Brief

Read this fully before writing code. It's the distilled spec from the
design conversation that produced this repo.

## Goal

Help a food-delivery courier (initially: Wolt couriers) decide where to
wait between orders to maximize the chance of getting picked for the
next nearby order. The system learns this from real stop locations
over time instead of asking users to log anything.

## Non-negotiable constraints

- **Never integrate with Wolt directly.** No scraping their app's API,
  no accessibility-service reading of Wolt's screen content, no
  account credentials stored anywhere. The only Wolt-related signal we
  use is *whether the Wolt app was recently in the foreground*, via
  Android's `UsageStatsManager` — this is a public, documented API
  about the user's own device, not an integration with Wolt's backend.
- **Zero manual data entry for any user**, including the first user.
  Onboarding is: pick a vehicle class, grant location, grant Usage
  Access, grant battery-optimization exemption. That's it.
- **No exact revenue/payout estimate.** We deliberately use order
  density as a proxy for earning potential. Do not add payout
  modeling back in.
- **Privacy by construction.** The server only ever stores aggregate
  counts keyed by (city, geohash cell, day_of_week, hour_bucket,
  vehicle_class). It never receives raw GPS traces, timestamps, or any
  user identifier. Do this reduction on-device before upload.

## How a "stop" is detected (Android)

1. A background process (location updates via `FusedLocationProviderClient`,
   ideally driven by significant-location-change callbacks rather than
   a fixed poll, to be battery-friendly) detects when the device has
   been roughly stationary for a few minutes.
2. When a stop ends, reverse-geocode / Places-lookup the location to
   confirm it's plausibly a venue or delivery address (best effort;
   don't block on this).
3. The stop is only logged if it falls inside an active "Wolt session"
   — see below.
4. The stop is reduced locally to `{geohash7, day_of_week, hour_bucket,
   vehicle_class}` and queued for batch upload. Raw coordinates and
   timestamps are discarded after this reduction.

## Wolt session tracking

- Poll `UsageStatsManager` for `MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND`
  events for the Wolt partner app's package name.
- A session is considered "active" if Wolt was foregrounded within the
  last **30 minutes** — this avoids requiring Wolt to be open at all
  times, since couriers background it constantly between tasks.
- Drive the check off location-update callbacks (which already wake
  the process periodically) rather than a separate fixed timer, to
  reduce the chance of Doze/battery optimization delaying it.
- Requires the user to grant "Usage Access" once via a settings deep
  link (`Settings.ACTION_USAGE_ACCESS_SETTINGS`) — not a runtime
  permission dialog.

## Vehicle classes

Two buckets, chosen at onboarding and never changed automatically:

- `fast` — car, motorcycle
- `slow` — bike, scooter

All storage and all queries are segmented by this class. A cyclist
should never see suggestions calibrated on car-reachable radii.

## Server data model

See `server/src/db/schema.sql`. One row per
`(city, geohash, day_of_week, hour_bucket, vehicle_class)`, holding a
running count and a decayed weight. Recent data should be weighted
more heavily than old data — implement this as an exponential decay
applied either on write (decay existing weight before adding the new
increment, based on time since `last_updated`) or on read; pick
whichever is simpler to reason about and document the choice.

## Minimum sample threshold

Don't surface a cell as a suggestion until it has accumulated roughly
**8–10 logged stops** in that exact bucket. Make this a server-side
config value, not hardcoded in multiple places, since it'll need
tuning per city density.

## Bootstrapping a new city

The repo owner will seed a city's data from their own historical stop
log before opening it to other users. There's no special "seed" API —
this just means running the normal ingestion pipeline against
historical data once. Don't build a separate seeding mechanism unless
it turns out to be needed.

## What exists in this scaffold right now

- Directory structure and build config stubs (Android Gradle files,
  Node/TypeScript server config).
- `server/src/db/schema.sql` — real schema, ready to use.
- Stub classes/files with comments describing intended responsibility,
  but little to no real logic yet.
- `docs/DECISIONS.md` — rationale log from the design conversation, for
  context on *why*, not just *what*.

## Suggested build order

1. Server: implement the schema migration, the `POST /v1/stops/batch`
   ingestion endpoint, and the `GET /v1/heatmap` query endpoint
   (including the decay and minimum-threshold logic). Get this fully
   working and testable (e.g. with a local Postgres via Docker) before
   touching Android.
2. Android: implement geohashing + the local Room queue first, with a
   manual "simulate a stop" debug button so the upload path can be
   tested without waiting on real GPS behavior.
3. Android: implement real stop detection (location callbacks +
   stationarity check) and Wolt session tracking.
4. Android: onboarding flow (vehicle class picker + the three
   permission requests) and the heatmap suggestion UI.
5. Wire it end to end, then iterate on thresholds/decay constants using
   real data.

Work in small increments and confirm each layer works before building
the next one on top of it. Ask before adding new third-party
dependencies beyond what's already stubbed in `package.json` /
`build.gradle.kts`.
