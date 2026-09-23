# Design Decisions

A short log of the reasoning behind choices in CLAUDE.md, so future
changes are made with the original tradeoffs in mind.

**No Wolt integration of any kind.** Wolt has no public API for
delivery partners (their developer APIs are merchant-facing only:
Wolt Drive, Order API). Reverse-engineering the partner app's private
API was considered and rejected — it breaks Wolt's terms of service,
risks account suspension, and breaks every time Wolt updates their
app. Reading Wolt's own screen content via an Accessibility Service to
get exact payout was also considered and rejected for the same
reasons, plus it isn't distributable on Google Play for non-accessibility
use. The only thing we read about Wolt is whether *our own device*
recently had it in the foreground, which is a self-contained Android
API, not a Wolt integration.

**Density instead of exact revenue.** Distance-based payout estimation
was prototyped conceptually (route distance for the paid leg, residual
fit against logged real payouts for tips/surge) but added real
complexity: needing a manually-logged calibration set, needing to
re-calibrate per city/market because fee formulas and vehicle mix
differ, and still only producing an estimate range. Order density is a
reasonable proxy for "more deliveries per hour" without any of that,
and needs zero ground-truth payout data from anyone, including the
first user.

**Aggregated server storage only.** Originally considered per-user
trip logs server-side. Switched to city/cell/day/hour/vehicle-class
counters because it sidesteps most privacy concerns outright (nothing
identifying is ever transmitted) and keeps the schema and queries
trivial.

**Vehicle class split (fast/slow).** A bike's reachable radius between
orders is much smaller than a car's, so unsegmented heatmaps would
mix irrelevant signal. One onboarding question solves this cheaply.

**30-minute Wolt-session window instead of foreground-only.** Couriers
realistically background the Wolt app constantly while waiting,
driving, etc. Treating "Wolt open right now" as the session boundary
would massively undercount real working time.

**Minimum sample threshold (~8-10).** Without a floor, a single stop
in an otherwise-quiet cell would look identical to a real hotspot.
The exact number is a tuning knob, not a hard constraint — expect it
to vary by city density and cell size.
