# Wolt Heatmap

A two-part system that helps delivery couriers position themselves in
higher-order-density zones between deliveries, without requiring any
manual input from end users.

- **android-app/** — Kotlin app that detects delivery stops in the
  background, tags them with a geohash/time/vehicle bucket, and shows
  the courier where order density is currently highest nearby.
- **server/** — thin aggregation API (Node/TypeScript + Postgres) that
  stores anonymized stop counts per city/cell/day/hour/vehicle-class
  and serves heatmap queries back to clients.

See `CLAUDE.md` for the full project brief and current implementation
status, and `docs/DECISIONS.md` for the reasoning behind the design
choices below.

## Core design decisions (short version)

1. No Wolt API/account integration of any kind — only on-device signals
   (location + which app is foregrounded) are used.
2. No manual order entry, for any user — onboarding asks one question
   (vehicle class) and requests two permissions.
3. No exact revenue estimate — order **density** is used as a proxy for
   earning potential.
4. Only aggregated counts (city, geohash cell, day-of-week, hour,
   vehicle class) are ever sent to the server — never raw trajectories
   or timestamps tied to an individual.
5. A minimum sample threshold gates when a cell is shown as a
   suggestion, to avoid one-off flukes looking like hotspots.
