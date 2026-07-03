# Implementation Plan: Android Vehicle Inspection Application

**Branch**: `001-vehicle-inspection-app` | **Date**: 2026-07-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-vehicle-inspection-app/spec.md`

> **Update (2026-07-03) — checklist-first workflow.** The delivered app evolved from the
> linear capture wizard (below) to a **catalog-driven, checklist-first** flow: after
> identification the checklist is the primary interface, and photo capture + damage marking
> happen per checklist item. This added `ChecklistCatalog`/`ChecklistResponse` (Room
> `checklist_responses`), per-item image tagging (`InspectionImage.checklistSectionId`/
> `checklistItemId`, DB **v4**), granular condition grades, continuous capture, image/inspection
> deletion, and a redesigned, size-bounded PDF + item-nested JSON report. See
> `checklist-integration-plan.md` (§11), spec.md FR-049…FR-062, and tasks.md Phase 12. The
> Summary/Technical-approach paragraphs below describe the original wizard and remain valid for
> the capture/AI/report primitives that the checklist reuses.

## Summary

A native, offline-first Android application that guides vehicle inspectors through a
structured, position-by-position capture flow (25 exterior + 25 interior positions),
validates image quality on-device, runs AI-assisted damage detection (Gemini Vision) on
every image, supports unlimited manual annotations with AI re-verification, performs a
final AI verification producing scores and integrity flags, and generates a structured
JSON report. Data is captured locally (Room + file storage) as the source of truth and
synced to Firebase (Firestore + Storage) in the background with retry.

**Technical approach**: Kotlin-only, Jetpack Compose + Material 3 UI, MVVM presentation
with unidirectional data flow (`StateFlow`), Clean Architecture (presentation / domain /
data), feature-based modularization, Hilt DI, Coroutines + Flow, Repository pattern over
Room + Firestore + Storage + Gemini + CameraX + ML Kit, Navigation Compose for the wizard
graph, and Coil for image loading. AI responses are strictly validated before use.

## Technical Context

**Language/Version**: Kotlin 2.0+ (100% Kotlin), JVM target 17, Compose Compiler via
Kotlin Compose plugin.

**Primary Dependencies**:
- UI: Jetpack Compose (BOM), Material 3, Navigation Compose, Coil (Compose)
- DI: Hilt (+ `hilt-navigation-compose`)
- Async: Kotlin Coroutines, Flow
- Local storage: Room (with KSP), DataStore (preferences/session)
- Camera & vision: CameraX (core/camera2/lifecycle/view), ML Kit Text Recognition (OCR)
- Backend: Firebase BOM — Auth, Firestore, Storage, Crashlytics, Analytics; App Check
- AI: Gemini Vision via the Google AI (Generative AI) client, accessed behind a domain port
- Background work: WorkManager (+ Hilt integration) for sync/upload
- Serialization: kotlinx.serialization (report JSON + AI response parsing)
- Testing: JUnit4, Turbine (Flow), MockK, kotlinx-coroutines-test, Compose UI Test,
  Espresso, Hilt testing, Robolectric (JVM UI where feasible), Firebase emulator suite

**Storage**: Room (structured local source of truth) + app-internal encrypted file store
for images/thumbnails; Firebase Firestore (structured cloud data) + Firebase Storage
(image/report binaries). DataStore for session/preferences.

**Testing**: JVM unit tests for use cases and view models; instrumented UI tests for every
screen; integration tests for offline capture, sync/retry, and report generation; AI
response-validation tests.

**Target Platform**: Android, `minSdk = 26` (Android 8.0), `targetSdk`/`compileSdk = 35`.
Phone + tablet, portrait + landscape.

**Project Type**: Mobile (single Android app, multi-module by feature + core layers).

**Performance Goals**: No perceptible UI freeze during capture/save; on-device quality
validation < ~300 ms per image; image compression keeps typical upload payload small
without degrading analysis; smooth 60 fps scrolling in galleries.

**Constraints**: Offline-first (full inspection lifecycle usable with zero connectivity);
AI requires network (queued when offline); all AI output validated before use; encrypted
at rest; secure transport; accessible; localized; dark mode; tablet + landscape.

**Scale/Scope**: ~11 primary screens, 50 guided capture positions, 8 core entities, single
inspector per inspection; designed for thousands of inspections per inspector over time.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Plan Compliance | Status |
|---|-----------|-----------------|--------|
| I | Kotlin-First & Structured Concurrency | 100% Kotlin; Coroutines for async; Flow/StateFlow for state; injected dispatchers | PASS |
| II | Jetpack Compose + Material 3 | All UI in Compose + Material 3; single immutable state per screen | PASS |
| III | Layered Architecture (MVVM + Clean + Repository) | presentation/domain/data layers; domain framework-free; repos as domain ports | PASS |
| IV | Modular, Feature-Based & SOLID | `:core:*` + `:feature:*` modules; feature-owned layers; no cycles; interfaces across boundaries | PASS |
| V | Hilt DI | Hilt for all wiring; dispatchers + repos + AI clients injected via interfaces | PASS |
| VI | Offline-First | Room + file store as source of truth; WorkManager sync; UI never blocks on network | PASS |
| VII | Firebase Backend | Firestore/Storage/Auth behind repositories; SDK types confined to data layer | PASS |
| VIII | Validated Gemini Vision AI | Gemini behind domain port; every response schema-validated; safe fallback + retry | PASS |
| IX | Accessibility | Semantics, contrast, touch targets, font scaling; verified in UI tests | PASS |
| X | Test Discipline (NON-NEGOTIABLE) | Unit tests per use case; UI tests per screen; validation tests for AI | PASS |

**Platform constraints**: `minSdk = 26` respected; newer APIs gated. No violations.

**Result**: PASS (no entries required in Complexity Tracking).

## Project Structure

### Documentation (this feature)

```text
specs/001-vehicle-inspection-app/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (entities, Room + Firestore schema)
├── quickstart.md        # Phase 1 output (build/run/test)
├── contracts/           # Phase 1 output (domain ports + external contracts)
│   ├── README.md
│   ├── repositories.md
│   ├── ai-gemini-contract.md
│   ├── vin-decode-contract.md
│   ├── firestore-contract.md
│   ├── storage-contract.md
│   └── report-json-schema.json
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

Multi-module Gradle (Kotlin DSL) project. Layers within each feature module follow Clean
Architecture; shared concerns live in `:core:*` modules. `:domain` (pure Kotlin/JVM) has
no Android or vendor dependencies.

```text
vehicle-inspection/
├── app/                                  # Application module: DI graph, nav host, theme
│   └── src/main/java/com/vsp/inspection/
│       ├── VspApplication.kt             # @HiltAndroidApp
│       ├── MainActivity.kt               # @AndroidEntryPoint, single-activity host
│       └── navigation/                   # Root NavHost, top-level graph wiring
│
├── build-logic/                          # Convention plugins (android-library, compose, hilt, test)
│
├── core/
│   ├── model/          # :core:model  — pure Kotlin domain models (no Android)
│   ├── common/         # :core:common — Result/AppError, dispatchers, validators, utils
│   ├── domain/         # :core:domain — shared use cases, repository interfaces (ports)
│   ├── data/           # :core:data   — repository impls, Room, Firestore, Storage, sync
│   │   └── src/main/java/com/vsp/core/data/
│   │       ├── local/          # Room: entities, DAOs, database, TypeConverters, file store
│   │       ├── remote/         # Firestore + Storage data sources (SDK confined here)
│   │       ├── ai/             # Gemini client impl + response validators/mappers
│   │       ├── vin/            # VIN decode data source (OCR handoff + lookup)
│   │       ├── sync/           # WorkManager workers, SyncQueue, connectivity observer
│   │       ├── mapper/         # entity <-> domain mappers
│   │       └── repository/     # repository implementations
│   ├── datastore/      # :core:datastore — session + preferences (DataStore)
│   ├── camera/         # :core:camera — CameraX capture, quality validation, ML Kit OCR
│   ├── ui/             # :core:ui — Material 3 theme, design system, reusable Composables
│   └── testing/        # :core:testing — fakes, fixtures, test rules, MainDispatcherRule
│
└── feature/
    ├── auth/           # :feature:auth       — Login
    ├── dashboard/      # :feature:dashboard  — inspection list + start/resume
    ├── identify/       # :feature:identify   — Start (VIN + New/Old), Identify Vehicle,
    │                   #                        Vehicle Details, Old-vehicle Documentation
    ├── capture/        # :feature:capture    — Exterior + Interior guided capture
    ├── annotation/     # :feature:annotation — image detail, annotation, AI re-verify
    ├── review/         # :feature:review     — completeness, galleries, finalize
    ├── verification/   # :feature:verification — final AI verification + scores
    └── report/         # :feature:report     — report screen + share/export

    # Each :feature:* module:
    #   presentation/  (Composable screens, ViewModel, UiState, UiEvent)
    #   domain/        (feature use cases; depends on :core:domain ports)
    #   navigation/    (feature route + NavGraphBuilder extension)
    #   src/androidTest (UI tests)  +  src/test (unit tests)
```

**Structure Decision**: Multi-module by feature with shared `:core:*` layer modules,
selected to satisfy Principle IV (modular, feature-based) and Principle III (Clean
Architecture). The `:core:domain` and `:core:model` modules are pure Kotlin (no Android
SDK), enforcing inward dependencies. Vendor SDKs (Firebase, Gemini, CameraX, ML Kit) are
confined to `:core:data` and `:core:camera`, keeping the domain and presentation layers
vendor-agnostic (Principles III, VII, VIII).

## Architecture

### Layered dependency rule

```text
presentation (feature UI + ViewModel)
      │  depends on
      ▼
domain (use cases + repository interfaces + models)   ← pure Kotlin, no Android/vendor
      ▲  implemented by
      │
data (repository impls, Room, Firestore, Storage, Gemini, CameraX, sync)
```

- Composables render an immutable `UiState`; emit `UiEvent`/intents to the ViewModel.
- ViewModel exposes `StateFlow<UiState>`, invokes use cases, maps domain results to state.
- Use cases contain business rules; single responsibility; return `Result<T, AppError>`.
- Repository interfaces live in domain; implementations in data assemble local + remote +
  AI data sources and own the offline-first read/write policy.

### Unidirectional data flow (per screen)

`UiEvent → ViewModel.onEvent() → UseCase → Repository → (Room/Remote/AI) → Flow → UiState → Compose`

### Navigation graph (Navigation Compose)

Single-activity, nested graphs. The inspection is a resumable linear wizard.

```text
RootNavHost
├── auth (start)
│   └── login  ──(success)──► dashboard
├── dashboard
│   ├── "Start Inspection" ─► inspection/{new}
│   ├── "Resume"           ─► inspection/{id} @ last step
│   └── "View Report"      ─► report/{inspectionId}
└── inspection/{inspectionId}   (nested graph — wizard)
    ├── startInspection  ─► (VIN entry + New/Old selection; Old ⇒ registration number)
    ├── identifyVehicle  ─► vehicleDetails
    ├── vehicleDetails   ─► (Old ⇒ oldVehicleDocs) | (New ⇒ exteriorCapture)
    ├── oldVehicleDocs   ─► exteriorCapture   (RC + PUC + Insurance photos; #ownerships; #keys)
    ├── exteriorCapture  ─► interiorCapture      (imageDetail/{imageId} reachable)
    ├── interiorCapture  ─► review
    ├── review           ─► finalVerification    (imageDetail/{imageId} reachable)
    ├── finalVerification─► report
    └── report/{inspectionId} ─► share/export | dashboard
imageDetail/{imageId}  (annotation + AI re-verify; reachable from capture/review)
```

Routes are type-safe (Navigation Compose typed routes / sealed route objects). Each
feature contributes a `NavGraphBuilder.<feature>Graph()` extension; the app module wires
them into the root host. Deep-link to resume uses `inspection/{id}` + saved current step.
The `oldVehicleDocs` step is conditionally included only when the vehicle is classified as
Old; New vehicles route directly from `vehicleDetails` to `exteriorCapture`. The exterior
and interior capture steps are strict, sequence-ordered wizards: the inspector advances one
position at a time through the defined order and cannot skip ahead past an unaddressed
mandatory position.

### State management

- One `data class XxxUiState` per screen (immutable), exposed as `StateFlow`.
- `SavedStateHandle` for process-death-safe screen args; Room is the durable source of
  truth so wizard progress survives process death and app kill.
- Sync status is a derived `Flow` from the local store's per-item sync fields.
- Long-running AI/sync work is delegated to use cases/WorkManager, never held in Compose.

## Error handling

A single typed error model in `:core:common`:

```text
sealed interface AppError {
  Network(retryable)       // connectivity/timeouts
  Auth                     // invalid/expired session
  Permission(type)         // camera/location/mic denied
  ImageQuality(reason)     // blurry/dark/overexposed/incomplete
  AiUnavailable / AiInvalidResponse   // service down / failed validation
  VinLookupFailed          // triggers manual fallback
  Storage(full/io)         // device storage/io
  Unknown(cause)
}
```

- Use cases return `Result<T, AppError>` (a lightweight `sealed` result, not exceptions
  across layers). Repositories map data-source exceptions to `AppError`.
- ViewModels translate `AppError` into user-facing state (message, retry, fallback action).
- **AI**: `AiInvalidResponse` is produced when schema validation fails → never surfaced as
  a finding; UI offers retry; finding marked pending. Low-confidence → `reviewRequired`.
- **VIN**: `VinLookupFailed` routes to manual attribute selection (not an error dialog).
- **Permissions**: contextual rationale + settings deep-link; capture blocked until granted.
- **Crash reporting**: Crashlytics for non-fatals + fatals; AppError categories logged.

## Offline sync

- **Source of truth**: Room + encrypted file store. All reads served locally; all writes
  land locally first, then enqueue a sync task.
- **SyncQueue**: each mutating operation writes a `SyncTask` (entity type, entity id,
  op, payload ref, status, attempt count). Per-item `syncState`:
  `PENDING → UPLOADING → SYNCED | FAILED`.
- **Workers (WorkManager, Hilt)**: `ImageUploadWorker`, `InspectionSyncWorker`,
  `ReportUploadWorker`, and an `AiAnalysisWorker` (runs queued Gemini analysis when
  online). Constrained to `NetworkType.CONNECTED`; `BackoffPolicy.EXPONENTIAL`; unique
  work chains per inspection to preserve order (images → metadata → AI results → report).
- **Idempotency/resume**: deterministic Storage paths + Firestore doc ids keyed by local
  ids; uploads are idempotent so an app kill mid-sync resumes without duplicates.
- **Image pipeline**: capture → quality validate → compress (target long-edge + quality) →
  store file + Room row → enqueue upload; thumbnails generated locally for gallery.
- **Connectivity**: a `ConnectivityObserver` (Flow) triggers queue drain and updates UI.
- **Conflict policy**: single-inspector ownership → last-write-wins per inspection; drafts
  never overwritten by server.

## Database schema (local — Room)

Full column-level detail in [data-model.md](./data-model.md). Tables:
`inspectors`, `vehicles`, `inspections`, `inspection_images`, `ai_findings`,
`annotations`, `reports`, `audit_log`, `sync_tasks`. Indices on foreign keys and
`(inspectionId, position)`; `syncState` indexed for queue queries; cascade deletes from
`inspections`.

## Firestore schema

Full detail in [data-model.md](./data-model.md) and
[contracts/firestore-contract.md](./contracts/firestore-contract.md). Root collections
keyed by inspector and inspection; images/findings/annotations as subcollections under
each inspection; report stored as a document plus a JSON artifact in Storage.

## API design

Domain **ports** (interfaces) implemented in data; external contracts documented under
`contracts/`:

- `AuthRepository`, `VehicleRepository`, `InspectionRepository`, `ImageRepository`,
  `AnnotationRepository`, `AiAnalysisRepository`, `ReportRepository`, `SyncRepository`.
- External: Gemini Vision request/response contract (+ validation rules), VIN-decode
  contract, Firestore document contract, Storage path contract, Report JSON schema.

## Repositories

| Repository (domain port) | Responsibility | Data sources |
|--------------------------|----------------|--------------|
| `AuthRepository` | sign-in, session, offline session validity | Firebase Auth, DataStore |
| `VehicleRepository` | VIN decode, manual attributes, persist vehicle | VIN source, Room, Firestore |
| `InspectionRepository` | create/resume/update/finalize inspection | Room, Firestore |
| `ImageRepository` | save/validate/compress/list images, thumbnails | CameraX, file store, Room, Storage |
| `AnnotationRepository` | CRUD annotations | Room, Firestore |
| `AiAnalysisRepository` | detect, re-verify, final verify (validated) | Gemini port, Room |
| `ReportRepository` | build JSON report, persist, upload, share | Room, Storage, Firestore |
| `SyncRepository` | enqueue/observe/drain sync tasks | WorkManager, Room |

## UseCases (representative, one responsibility each)

- Auth: `SignInUseCase`, `ObserveSessionUseCase`, `SignOutUseCase`
- Dashboard: `ObserveInspectionsUseCase`, `StartInspectionUseCase`, `ResumeInspectionUseCase`
- Identify: `DecodeVinUseCase`, `ScanVinFromImageUseCase`, `SaveVehicleDetailsUseCase`
- Capture: `CapturePositionImageUseCase`, `ValidateImageQualityUseCase`,
  `SkipPositionUseCase`, `ObserveCaptureProgressUseCase`
- AI: `AnalyzeImageUseCase`, `ValidateAiResponseUseCase`, `ReverifyAnnotationUseCase`,
  `RunFinalVerificationUseCase`
- Annotation: `AddAnnotationUseCase`, `UpdateAnnotationUseCase`, `DeleteAnnotationUseCase`
- Review/Report: `GetInspectionCompletenessUseCase`, `FinalizeInspectionUseCase`,
  `GenerateReportUseCase`, `ShareReportUseCase`
- Sync: `EnqueueSyncUseCase`, `ObserveSyncStatusUseCase`, `RetryFailedUploadsUseCase`

Every use case has unit tests (success/failure/boundary) per Principle X.

## ViewModels

One per screen, exposing `StateFlow<UiState>` + `onEvent(UiEvent)`:
`LoginViewModel`, `DashboardViewModel`, `IdentifyVehicleViewModel`,
`VehicleDetailsViewModel`, `CaptureViewModel` (parameterized exterior/interior),
`ImageDetailViewModel` (annotation + re-verify), `ReviewViewModel`,
`FinalVerificationViewModel`, `ReportViewModel`. Dispatchers injected; no business logic
in Composables.

## Testing strategy

- **Unit (JVM, `src/test`)**: every use case; view models via Turbine + coroutines-test;
  AI response validators (valid/invalid/malformed/low-confidence); mappers; error mapping.
  Tools: JUnit4, MockK, Turbine, `MainDispatcherRule`.
- **UI/instrumented (`src/androidTest`)**: every screen (rendering, primary interactions,
  skip-with-reason, accessibility semantics) via Compose UI Test + Hilt test rules; fakes
  from `:core:testing`.
- **Integration**: offline capture → local persistence; sync/retry via WorkManager test
  harness against Firebase emulator; end-to-end report generation.
- **Contract**: Gemini response schema validation; Firestore document shape; report JSON
  schema conformance.
- **Gates**: merges blocked when a new/changed use case or screen lacks tests, or tests
  fail (Principle X). Accessibility assertions included in screen tests (Principle IX).
- **Coverage focus**: domain + validation logic prioritized; target high coverage on
  use cases and AI validation.

## CI/CD

- **Pipeline (GitHub Actions)** on PR + main:
  1. `assemble` (all modules) with Gradle cache
  2. `ktlint`/`detekt` static analysis + `lint`
  3. Unit tests (`testDebugUnitTest`) with report artifacts
  4. Instrumented tests on emulator (Gradle Managed Devices / `connectedCheck`) with
     Firebase emulator suite for integration tests
  5. Coverage report (Kover) with threshold gate
  6. Assemble signed release (protected branches) with secrets-managed keystore
- **Config/secrets**: `google-services.json`, signing keys, and Gemini API key injected
  via CI secrets (never committed); `local.properties`/env for local dev.
- **Quality gates**: build + lint + tests + coverage must pass to merge; Crashlytics
  mapping upload on release.
- **Versioning**: semantic version + build number; changelog per release.

## Security

- **Data at rest**: Room encrypted (SQLCipher) or field-level encryption for sensitive
  data; image files in app-internal storage encrypted; DataStore encrypted for session.
- **Transport**: HTTPS/TLS everywhere; Firebase security rules restrict access to the
  owning inspector; Firebase App Check to attest app integrity for Firestore/Storage.
- **Auth**: enforced before any data access; offline access only with a previously valid
  session; sign-out clears local secrets.
- **AI safety**: all Gemini responses validated against schema before use; prompts avoid
  leaking PII beyond the image; API key kept server-agnostic and injected securely.
- **Input validation**: VIN, form fields, and all external/AI inputs validated.
- **Least privilege**: runtime permissions requested contextually; minimal scopes.
- **Auditability**: audit log of significant events; Crashlytics for anomaly detection.
- **Privacy**: images/metadata treated as sensitive records; retention configurable.

## Complexity Tracking

> No constitution violations. Multi-module structure is justified directly by Principles
> III and IV (Clean Architecture + feature modularity), not added complexity.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none) | — | — |
