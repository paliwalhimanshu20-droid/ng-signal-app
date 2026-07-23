# JARVIS Android Application — Sprint-7

Native Android client for JARVIS OS. Jetpack Compose, Material Design 3, MVVM, Hilt DI, Navigation Compose.

## Read this before anything else

Two things are different about this sprint compared to Sprint-0 through Sprint-6, and both are stated here plainly rather than left for you to discover:

### 1. This code was never compiled

The sandbox this was built in has a JVM but no `kotlinc`, no Gradle, and no network access to Google's or Maven Central's repositories (both return `403` from this environment — confirmed, not assumed). Every Python sprint (0–6) was verified by actually running `pytest` against your live repo. This sprint could not be verified that way. It is carefully hand-written, idiomatic Kotlin/Compose — but it is the first code in this project delivered without a real build behind it.

**What to do about it:** push this to GitHub and let `.github/workflows/android-build.yml` build it — that workflow installs a real JDK and Gradle and runs `gradle assembleDebug` plus the unit tests. That is the actual first compile check. If it fails, that's expected-possible, not a sign anything else in this project is untrustworthy — it's a sign this one sprint skipped the verification step every other sprint had.

### 2. There is no backend for this app to actually call yet

Sprints 0–6 built `jarvis-os` as a Python console application — `main.py` reads from `stdin`. There is no HTTP server, no REST endpoint, nothing network-reachable. So "Android must consume existing backend" (the sprint brief's own requirement) couldn't happen literally — there's nothing to consume.

What this app does instead: every repository interface (`ConnectionRepository`, `ApprovalRepository`, etc., in `data/repository/`) has method names and parameter shapes that mirror the real Python backend's public methods 1:1 — e.g. `ConnectionRepository.approve(connectionId, approvedBy)` mirrors `jarvis.connections.ConnectionManager.approve(connection_id, approved_by)` exactly. Every screen and ViewModel depends only on the interface, never on the `Mock*` implementation directly (see `di/RepositoryModule.kt`). The day a real API exists, swapping `MockConnectionRepository::class` for a `RemoteConnectionRepository::class` in that one file is the entire migration — no ViewModel, no screen, no navigation code changes.

Until then, **every screen in this app is driven by real, working, in-memory mock data** — approve/reject/suspend/disconnect genuinely transition state and genuinely enforce the same rules the Python `ConnectionManager` does (a rejected connection has no code path to `CONNECTED`, tested directly). It's a faithful behavioral stand-in, not a placeholder UI that always succeeds.

## What's fully implemented

- **Design system** — colors, typography (with a real font-scale multiplier), shapes, spacing, Dark/Light/AMOLED theming, curated accent-color palette
- **Navigation** — bottom bar + drawer, all 8+ destinations from Part 3
- **Home Dashboard** — real drag-to-reorder (long-press the handle), hide/show via bottom sheet, **persisted through DataStore** (Acceptance Scenario 5)
- **Settings → Appearance** — mode, accent color, font, font size, all **persisted through DataStore** (Acceptance Scenario 2)
- **Connections** — full lifecycle (approve/reject/suspend/disconnect/reconnect/test/disable-all), matching Sprint-6's governance rules exactly (Acceptance Scenarios 1–4)
- **Approval Center** — pending + history, approve/reject
- **Home Automation** — the Part 12 safety allowlist is **enforced in code**, at two separate points (UI never renders a toggle for an unsupported device; the repository re-checks and rejects even if called directly). Covered by unit tests proving a door lock/camera toggle is refused.
- **Chat, Projects, Memory, Notifications** — real, functional screens on mock data; Chat has no live AI call (voice button is UI-only, per Sprint-7's explicit scope)

## What's intentionally condensed (see Sprint-8 recommendations in the main delivery)

- Settings sections beyond Appearance (Voice, AI, Connections, Notifications, Privacy) are designed but not fully built out — building five more full settings sub-screens at the same depth as Appearance, unverified by any compiler, was a scope call, not an oversight
- No wallpaper/background-image picker (needs real image storage — a genuinely separate concern)
- "View Audit" button on Connections — no audit data is plumbed from the Python side yet
- No push notifications — no backend event stream exists to notify from
- Wake-word and biometric auth — explicitly out of scope per the sprint brief

## Project structure

```
jarvis-android/
  app/src/main/java/com/jarvis/os/app/
    designsystem/       Color, Type, Shape, Theme, reusable components
    navigation/          JarvisDestination, JarvisNavHost
    data/model/          Domain models mirroring the Python backend
    data/repository/      Interfaces + Mock implementations (see note above)
    data/settings/        DashboardLayout, AppearanceSettings, SettingsRepository (real DataStore)
    di/                  Hilt modules
    feature/<name>/       One package per screen: Screen.kt + ViewModel
  app/src/test/          Plain JUnit4 unit tests — no Android instrumentation needed
  .github/workflows/     Debug-APK build + test workflow
```

## Building (once you have Android Studio, or via the Actions workflow)

```
cd jarvis-android
./gradlew assembleDebug   # if you generate a wrapper locally in Android Studio
# or, on this repo's CI: push and check the "Android Debug Build" workflow run
```

No Gradle wrapper JAR is committed (a binary this sandbox couldn't produce without network access) — Android Studio will offer to generate one the first time you open this project, or the CI workflow installs Gradle directly and doesn't need one at all.
