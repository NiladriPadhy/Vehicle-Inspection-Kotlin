# Quickstart: Android Vehicle Inspection Application

**Feature**: `001-vehicle-inspection-app`

## Prerequisites

- Android Studio (latest stable) with Android SDK, `compileSdk 35`, `minSdk 26`
- JDK 17
- A physical device or emulator running Android 8.0 (API 26) or higher with a camera
- Firebase project with Firestore, Storage, Auth, App Check, Crashlytics enabled
- A Gemini (Google AI) API key
- Firebase CLI (for the local emulator suite, used by integration tests)

## One-time setup

1. **Clone & open** the project in Android Studio.
2. **Firebase**: download `google-services.json` into `app/` (do NOT commit it).
3. **Secrets**: add to `local.properties` (git-ignored):
   ```properties
   GEMINI_API_KEY=your_key_here
   ```
   These are exposed to the build via `BuildConfig`/manifest placeholders; in CI they come
   from encrypted secrets.
4. **App Check**: register a debug token for local development.
5. **Sync Gradle**.

## Build & run

```bash
# Debug build
./gradlew :app:assembleDebug

# Install & launch on a connected device/emulator
./gradlew :app:installDebug
```

## Run tests

```bash
# All unit tests (JVM) — use cases, view models, AI validators, mappers
./gradlew testDebugUnitTest

# Static analysis + lint
./gradlew ktlintCheck detekt :app:lintDebug

# Coverage
./gradlew koverHtmlReport

# Instrumented UI tests (every screen) — device/emulator required
./gradlew connectedDebugAndroidTest

# Integration tests with Firebase emulator
firebase emulators:exec "./gradlew connectedDebugAndroidTest"
```

## Verifying the primary flow (manual smoke test)

1. Launch the app → **Login** with a valid account.
2. **Dashboard** → tap **Start Inspection**.
3. **Identify Vehicle**: enter a VIN (or scan) → confirm decoded details, or use manual
   fallback → **Vehicle Details** → proceed.
4. **Exterior Inspection**: capture positions following the overlay/angle/distance guides;
   confirm blurry/dark/overexposed images are rejected; skip a position and provide a
   reason.
5. **Interior Inspection**: repeat for interior positions.
6. Open an image → add a **rectangle/pin annotation**, set damage type + severity, comment;
   trigger **AI re-verify**.
7. **Review**: confirm completeness gating; finalize.
8. **AI Final Verification**: confirm scores + integrity flags appear.
9. **Report**: confirm the report renders (summary, galleries, damage list, scores,
   recommendation).
10. Toggle airplane mode during capture to confirm **offline-first**; re-enable to confirm
    **automatic background sync** with per-item status.

## Module map (where things live)

| Concern | Module |
|---------|--------|
| App entry, nav host, theme | `:app` |
| Domain models, enums | `:core:model` |
| Result/AppError, dispatchers, validators | `:core:common` |
| Use cases + repository ports | `:core:domain` |
| Room, Firestore, Storage, Gemini, sync, mappers | `:core:data` |
| Session/preferences | `:core:datastore` |
| CameraX capture + quality + ML Kit OCR | `:core:camera` |
| Material 3 design system, shared UI | `:core:ui` |
| Test fakes/fixtures | `:core:testing` |
| Screens (auth/dashboard/identify/capture/annotation/review/verification/report) | `:feature:*` |

## Constitution guardrails (must hold)

- 100% Kotlin; Compose + Material 3 only; Coroutines + Flow.
- MVVM + Clean Architecture; data access only via repository ports; domain has no
  Android/vendor deps.
- Hilt for all DI; dispatchers injected.
- Offline-first: Room + file store is the source of truth; UI never blocks on network.
- Every AI response validated before use.
- Every use case has unit tests; every screen has UI tests; accessibility verified.
