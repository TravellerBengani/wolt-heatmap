# android-app

Kotlin app, plain Gradle project layout (open the `android-app/` folder
directly in Android Studio, or point Claude Code / `gradle` at it).

Module responsibilities, see inline TODOs in each file for specifics:

- `onboarding/VehicleClassPicker.kt` — the one onboarding question.
- `detection/StopDetectionService.kt` — background stop detection.
- `detection/WoltSessionTracker.kt` — "was Wolt open in the last 30 min".
- `data/local/` — Room queue of pending uploads.
- `data/remote/HeatmapApi.kt` — Retrofit client for the server.
- `work/UploadWorker.kt` — periodic batch upload via WorkManager.
- `util/GeoUtils.kt` — geohash encode/decode helpers.

Nothing in this module should ever read Wolt's own data — only Android's
own `UsageStatsManager` (whether Wolt is foregrounded) and the device's
location.
