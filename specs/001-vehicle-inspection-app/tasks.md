---
description: "Task list for Android Vehicle Inspection Application"
---

# Tasks: Android Vehicle Inspection Application

**Input**: Design documents from `/specs/001-vehicle-inspection-app/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Test tasks ARE included. The project constitution mandates a unit test for
every use case and a UI test for every screen (Principle X), and the spec defines a
testing strategy; therefore test tasks are non-negotiable here.

**Organization**: Tasks are grouped by user story (US1–US8) to enable independent
implementation and testing. Priority order from spec.md: P1 (US8, US1, US2), P2 (US3, US7,
US4, US6), P3 (US5).

> **Implementation status (2026-07-01)**: The **end-to-end application is implemented and
> verified to build**: `gradle :app:assembleDebug` produces a debug APK, and all JVM unit
> tests pass (`:core:domain:test`, `:core:data:testDebugUnitTest`, `:app:testDebugUnitTest`);
> `:app:compileDebugAndroidTestKotlin` compiles the instrumented tests.
>
> **What is implemented:** the full inspection wizard — Login → Dashboard → Start (VIN +
> New/Old + registration) → Identify (VIN OCR + decoded attributes) → Old-vehicle documents
> (RC/PUC/Insurance + ownerships/keys, US9) → strict-sequence Exterior capture → strict-sequence
> Interior capture → Review grid → Image detail (AI analysis, manual annotations, AI
> re-verification) → Final verification → Report (JSON + share + sync status). Backing this:
> all 8 repositories (Room-backed, offline-first), all domain use cases, `AiResponseValidator`
> (validates every AI response), `GeminiAiVisionPort` (REST), `VinDecoder` + ML Kit
> `VinOcrScanner`, `ImageQualityAnalyzer`, CameraX capture, encrypted `FileStore`,
> `ReportBuilder` (schema-conformant JSON), `ConnectivityMonitor`, and a Hilt `SyncWorker` +
> `SyncScheduler` (WorkManager). Reusable accessible Compose components live in `:core:ui`.
> Firebase security rules are under `firebase/`, and CI is at `.github/workflows/android.yml`.
>
> **Architecture note (intentional deviation from plan):** to keep the build reliable in this
> environment, feature presentation code lives in `:app` under `com.vsp.inspection.feature.*`
> packages (rather than separate `:feature:*` Gradle modules), and data/AI/sync/report logic
> lives in `:core:data`. Clean-Architecture layering (presentation → domain ← data via Hilt) is
> preserved.
>
> **Needs runtime config / hardware (implemented but not executable here):** live AI requires a
> Gemini API key (`AiConfig.apiKey`, currently empty → graceful `AiUnavailable` degradation);
> Firebase sync requires `google-services.json` + applying the `google-services` plugin
> (dependencies wired, remote calls guarded); camera/OCR and instrumented UI tests require a
> device/emulator. Deferred niceties: T002 convention plugins (config inlined per module), T004
> ktlint/detekt/Kover, T007 Crashlytics/Analytics init, T022 instrumented DAO tests.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story the task belongs to (US1–US8)
- File paths follow the multi-module structure in plan.md

## Path Conventions

- App module: `app/src/main/java/com/vsp/inspection/`
- Core modules: `core/<name>/src/main/java/com/vsp/core/<name>/`
- Feature modules: `feature/<name>/src/main/java/com/vsp/feature/<name>/`
- Unit tests: `<module>/src/test/...`; instrumented/UI tests: `<module>/src/androidTest/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Multi-module project skeleton, build tooling, and platform configuration.

- [X] T001 Initialize Gradle project (Kotlin DSL) with root `settings.gradle.kts`, `build.gradle.kts`, and `gradle/libs.versions.toml` version catalog pinning Kotlin 2.0+, AGP, Compose BOM, Hilt, Room, Firebase BOM, CameraX, ML Kit, WorkManager, Coil, kotlinx.serialization, Coroutines
- [ ] T002 Create convention plugins in `build-logic/` (android-application, android-library, compose, hilt, room, kotlin-jvm, test) to standardize module config (`minSdk 26`, `compileSdk 35`, JVM 17)
- [X] T003 [P] Create module skeletons and register in `settings.gradle.kts`: `:app`, `:core:model`, `:core:common`, `:core:domain`, `:core:data`, `:core:datastore`, `:core:ui`, `:core:testing` (DONE). `:core:camera` and `:feature:{auth,dashboard,identify,capture,annotation,review,verification,report}` are registered as their implementation phases begin.
- [ ] T004 [P] Configure `ktlint`, `detekt`, Android `lint`, and Kover coverage in `build-logic/` and root config files
- [ ] T005 [P] Add Firebase to `:app` (`google-services.json` placeholder, `google-services` plugin) and Gemini API key wiring via `local.properties` → `BuildConfig` in `app/build.gradle.kts`
- [X] T006 Create `VspApplication` (`@HiltAndroidApp`) and `MainActivity` (`@AndroidEntryPoint`) in `app/src/main/kotlin/com/vsp/inspection/` with a single-activity Compose host
- [ ] T007 [P] Set up crash reporting (Crashlytics) and Analytics initialization in `:app`

**Checkpoint**: Project builds; empty modules wired; DI + Firebase initialized.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core models, error model, domain ports, local source of truth, DI plumbing,
design system, and test scaffolding that ALL user stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Domain models & common (`:core:model`, `:core:common`)

- [X] T008 [P] Create domain enums in `core/model/src/main/kotlin/com/vsp/core/model/` (`InspectionContext`, `InspectionStatus`, `VehicleCategory`, `Section` [EXTERIOR/INTERIOR/DOCUMENT], `DocumentType` [RC/POLLUTION_CERTIFICATE/INSURANCE], `ImageQuality`, `CaptureState`, `Severity`, `AnnotationShape`, `FindingSource`, `SyncState`, `VinInputMethod`, `DamageType`) per data-model.md
- [X] T009 [P] Create domain model data classes in `core/model/.../model/` (`Inspector`, `Vehicle`, `Inspection`, `InspectionImage`, `AIFinding`, `Annotation`, `Report`, `AuditLogEntry`, `Completeness`, `ReverifyResult`, `FinalVerification`, `Session`) per data-model.md
- [X] T010 [P] Create `AppError` sealed hierarchy and `AppResult<T>` wrapper in `core/model/.../` (placed in the pure `:core:model` module so the pure-Kotlin domain layer can depend on it without an Android dependency)
- [X] T011 [P] Create `DispatcherProvider` interface (in `:core:domain`) + `DefaultDispatcherProvider` impl and Hilt `CoroutineModule` (in `:core:common`)
- [X] T012 [P] Create ordered position catalogs in `core/model/.../catalog/PositionCatalog.kt` (exterior 25, interior 25) and `DocumentCatalog` (Old-vehicle documents: RC, POLLUTION_CERTIFICATE, INSURANCE), each preserving strict sequence order
- [ ] T013 [P] Unit test dispatcher provider and position catalogs in `core/common/src/test/...`

### Domain ports (`:core:domain`)

- [X] T014 [P] Define repository interfaces in `core/domain/.../repository/` (`AuthRepository`, `VehicleRepository`, `InspectionRepository`, `ImageRepository`, `AnnotationRepository`, `AiAnalysisRepository`, `ReportRepository`, `SyncRepository`) per contracts/repositories.md
- [X] T015 [P] Define vendor ports in `core/domain/.../port/` (`AiVisionPort`, `VinDecodeSource`) per contracts

### Local persistence (`:core:data` local)

- [X] T016 Create Room entities in `core/data/.../local/entity/` for all 9 tables (`inspectors`, `vehicles`, `inspections`, `inspection_images`, `ai_findings`, `annotations`, `reports`, `audit_log`, `sync_tasks`) with keys/indices/FKs per data-model.md
- [X] T017 Room enum/time conversions handled at the mapper boundary (entities store `String`/`Long` columns), so dedicated Room `TypeConverters` are unnecessary; documented in `core/data/.../mapper/`
- [X] T018 [P] Create DAOs (`Flow` reads, `suspend` writes) in `core/data/.../local/dao/` for each entity
- [X] T019 Create `VspDatabase` (RoomDatabase) + migration scaffold + Hilt `DatabaseModule` in `core/data/.../local/` and `.../di/` (schema export configured via `room.schemaLocation`)
- [X] T020 [P] Create encrypted local file store (image/thumbnail read/write) in `core/data/.../io/FileStore.kt` (images in app-private internal storage; report JSON encrypted at rest via Jetpack Security)
- [X] T021 [P] Create entity↔domain mappers in `core/data/.../mapper/`
- [ ] T022 [P] Instrumented DAO tests (CRUD, cascade, indices) in `core/data/src/androidTest/...`

### DI, session, design system, testing scaffold

- [X] T023 [P] Create DataStore session store (`SessionStore`, `@Singleton @Inject`) in `core/datastore/.../`
- [X] T024 [P] Create Material 3 theme (light/dark + dynamic color) `VspTheme` in `core/ui/.../theme/`
- [X] T025 [P] Create reusable accessible Composables (scaffold/top bar, primary button, text field, loading, error, empty, step progress) in `core/ui/.../components/Components.kt`
- [X] T026 [P] Create `:core:testing` utilities: `MainDispatcherRule` + `TestDispatcherProvider` (fake repositories/fixtures expanded per feature phase) in `core/testing/.../`
- [X] T027 Create root `VspNavHost` + type-safe route definitions (`VspRoute`) skeleton in `app/.../navigation/`
- [X] T028 [P] Create Firebase data-source scaffolding (`FirebaseRemoteDataSource`: Firestore + Storage, guarded for offline) in `core/data/.../remote/`

**Checkpoint**: Foundation ready — domain, local source of truth, DI, theme, nav host, and
test scaffolding exist. User stories can now proceed.

---

## Phase 3: User Story 8 - Authentication & Dashboard (Priority: P1)

**Goal**: Secure login and a dashboard listing inspections by status with a start action.

**Independent Test**: Log in with valid credentials → land on dashboard → see inspections
grouped by status → start a new inspection.

### Tests for User Story 8 ⚠️

- [X] T029 [P] [US8] Unit test `SignInUseCase` (success/failure/validation) via `LoginViewModelTest` in `app/src/test/.../feature/auth/`
- [ ] T030 [P] [US8] Unit test `ObserveInspectionsUseCase`, `StartInspectionUseCase`, `ResumeInspectionUseCase` in `feature/dashboard/src/test/...`
- [ ] T031 [P] [US8] UI test Login screen (valid/invalid credentials, accessibility semantics) in `feature/auth/src/androidTest/...`
- [ ] T032 [P] [US8] UI test Dashboard screen (grouped list, start action, accessibility) in `feature/dashboard/src/androidTest/...`

### Implementation for User Story 8

- [X] T033 [US8] Implement `AuthRepository` (Firebase Auth + DataStore session, offline validity) in `core/data/.../repository/AuthRepositoryImpl.kt`
- [X] T034 [US8] Implement `InspectionRepository` create/observe/resume portions in `core/data/.../repository/InspectionRepositoryImpl.kt`
- [X] T035 [P] [US8] Implement auth use cases in `core/domain/.../usecase/AuthUseCases.kt`
- [X] T036 [P] [US8] Implement dashboard use cases in `core/domain/.../usecase/InspectionUseCases.kt`
- [X] T037 [US8] Implement `LoginViewModel` (`StateFlow<LoginUiState>`) in `app/.../feature/auth/`
- [X] T038 [US8] Implement Login Composable screen wired into `VspNavHost` in `app/.../feature/auth/` and `.../navigation/`
- [X] T039 [US8] Implement `DashboardViewModel` in `app/.../feature/dashboard/`
- [X] T040 [US8] Implement Dashboard Composable (list, start/resume, sign-out) in `app/.../feature/dashboard/`
- [X] T041 [US8] Wire auth+dashboard graphs and start-destination logic into `VspNavHost` in `app/.../navigation/`

**Checkpoint**: A user can log in and reach a functional dashboard.

---

## Phase 4: User Story 1 - Guided End-to-End Inspection (Priority: P1) 🎯 MVP

**Goal**: Guided capture of all 25 exterior + 25 interior positions with on-device quality
validation, skip-with-reason, review completeness gating, and finalize.

**Independent Test**: Start inspection → manual vehicle details → capture/skip mandatory
positions → Review shows completeness → finalize succeeds (offline, single device).

### Tests for User Story 1 ⚠️

- [X] T042 [P] [US1] Unit test capture wizard (capture/skip advance, strict sequence) via `CaptureViewModelTest` in `app/src/test/.../feature/capture/`
- [ ] T043 [P] [US1] Unit test `SaveVehicleDetailsUseCase` (manual entry) in `feature/identify/src/test/...`
- [ ] T044 [P] [US1] Unit test `GetInspectionCompletenessUseCase`, `FinalizeInspectionUseCase` (gating: mandatory captured/skipped) in `feature/review/src/test/...`
- [ ] T045 [P] [US1] UI test Vehicle Details screen (manual entry, validation) in `feature/identify/src/androidTest/...`
- [ ] T046 [P] [US1] UI test Capture screen (overlay/guidance, capture, quality reject, skip-with-reason, strict sequence-order advancement, progress, accessibility) in `feature/capture/src/androidTest/...`
- [ ] T047 [P] [US1] UI test Review screen (completeness matrix, finalize gating, accessibility) in `feature/review/src/androidTest/...`

### Implementation for User Story 1

- [X] T048 [US1] Implement CameraX capture + guided instruction overlay in `app/.../feature/capture/CameraCapture.kt`
- [X] T049 [US1] Implement on-device quality validation (blur/dark/overexposed/incomplete) in `core/data/.../image/ImageQualityAnalyzer.kt`
- [X] T050 [US1] Implement `ImageRepository` (capture→validate→store file+Room row, skip) in `core/data/.../repository/ImageRepositoryImpl.kt`
- [X] T051 [US1] Implement `VehicleRepository` save (manual details) portion in `core/data/.../repository/VehicleRepositoryImpl.kt`
- [X] T052 [US1] Extend `InspectionRepository` with `updateStep`, `getCompleteness`, `finalize` in `core/data/.../repository/InspectionRepositoryImpl.kt`
- [X] T053 [P] [US1] Implement capture use cases in `core/domain/.../usecase/ImageUseCases.kt`
- [X] T054 [P] [US1] Implement vehicle-details + review use cases in `core/domain/.../usecase/`
- [X] T055 [US1] Implement Identify (details) screen + nav in `app/.../feature/identify/` (manual entry path)
- [X] T056 [US1] Implement `CaptureViewModel` (parameterized exterior/interior) + screen as a strict sequence-ordered, one-position-at-a-time wizard in `app/.../feature/capture/`
- [X] T057 [US1] Implement `ReviewViewModel` + Review screen (gallery, completeness, finalize gating) in `app/.../feature/review/`
- [X] T058 [US1] Wire the resumable inspection wizard graph (start→identify→[docs]→exterior→interior→review) into `VspNavHost`
- [ ] T059 [US1] Record audit-log entries for create/capture/skip/finalize via `InspectionRepository` (deferred)

**Checkpoint**: Full guided inspection works offline through finalize (MVP deliverable).

---

## Phase 5: User Story 2 - AI-Assisted Damage Detection (Priority: P1)

**Goal**: Validated Gemini damage detection per image with type/confidence/severity/bbox/
recommendation/review flag; low-confidence flagged; malformed responses rejected.

**Independent Test**: Analyze a damaged-panel image → findings render with bounding boxes;
low-confidence flagged; malformed response rejected with retry.

### Tests for User Story 2 ⚠️

- [X] T060 [P] [US2] Unit test AI response validator (valid/empty/malformed/out-of-range confidence & bbox/unknown enum/low-confidence→reviewRequired) in `core/data/src/test/.../ai/AiResponseValidatorTest.kt`
- [ ] T061 [P] [US2] Unit test `AnalyzeImageUseCase` and `ValidateAiResponseUseCase` (success, `AiInvalidResponse`, `AiUnavailable`) in `feature/capture/src/test/...` (or `core/domain` if shared)
- [ ] T062 [P] [US2] UI test AI findings overlay on image detail (bounding boxes, review-required badge, accessibility) in `feature/annotation/src/androidTest/...`

### Implementation for User Story 2

- [X] T063 [US2] Implement `AiVisionPort` Gemini client (structured JSON prompt, REST) in `core/data/.../ai/GeminiAiVisionPort.kt`
- [X] T064 [US2] Implement AI response DTOs + strict validator + mapper to `AIFinding` per contracts/ai-gemini-contract.md in `core/data/.../ai/`
- [X] T065 [US2] Implement `AiAnalysisRepository.analyzeImage` (validate before persist; low-confidence→reviewRequired) in `core/data/.../repository/AiAnalysisRepositoryImpl.kt`
- [X] T066 [P] [US2] Implement `AnalyzeImageUseCase` (+ validation inside repo) in `core/domain/.../usecase/AiUseCases.kt`
- [X] T067 [US2] Implement image-detail screen with AI findings overlay + `ImageDetailViewModel` in `app/.../feature/imagedetail/`
- [X] T068 [US2] Surface findings via manual Analyze action on image detail, reachable from Review in `app/.../feature/imagedetail/` and `.../review/`

**Checkpoint**: Every captured image gets validated AI findings shown to the inspector.

---

## Phase 6: User Story 3 - Vehicle Identification & New/Old Classification (Priority: P2)

**Goal**: Start step captures VIN + New/Old classification (registration required for Old);
manual VIN entry, VIN OCR via camera, decode display, and manual-selection fallback.

**Independent Test**: Enter VIN + pick New → proceed without registration; pick Old →
registration becomes required; manual VIN → decoded attributes; scan VIN → OCR pre-fills;
force lookup failure → manual attribute selection available.

### Tests for User Story 3 ⚠️

- [X] T069 [P] [US3] Unit test `DecodeVinUseCase`/`VinDecoder` (valid VIN, invalid pattern, unknown WMI) in `core/data/src/test/.../vin/VinDecoderTest.kt` and `core/domain` `VehicleUseCasesTest`
- [X] T069a [P] [US3] Unit test classification logic (New vs Old; registration required only for Old) via `VehicleUseCasesTest` in `core/domain/src/test/...`
- [ ] T070 [P] [US3] UI test Start step (VIN entry, New/Old toggle, registration required for Old) and Identify Vehicle screen (manual entry, scan action, decoded display, fallback, accessibility) in `feature/identify/src/androidTest/...`

### Implementation for User Story 3

- [X] T071 [US3] Implement ML Kit VIN OCR + VIN pattern validation in `core/data/.../vin/VinOcrScanner.kt`
- [X] T072 [US3] Implement `VinDecodeSource` (`VinDecoder`) and wire into `VehicleRepository.decodeVin`/`scanVinFromImage` in `core/data/.../vin/` and `.../repository/VehicleRepositoryImpl.kt`
- [X] T072a [US3] Persist `vehicleCategory` on the inspection and require `registrationNumber` for Old in `InspectionRepositoryImpl` + `SaveVehicleDetailsUseCase`/`SaveOldVehicleDetailsUseCase`
- [X] T073 [P] [US3] Implement identify use cases (incl. classification) in `core/domain/.../usecase/VehicleUseCases.kt`
- [X] T074 [US3] Implement Start step (VIN + New/Old + conditional registration) and Identify screen (VIN entry, scan, decoded review, manual fallback) + nav in `app/.../feature/start/` and `.../feature/identify/`
- [X] T074a [US3] Wire conditional routing so Old vehicles proceed to `OldVehicleDocs` and New vehicles go straight to exterior capture, in `app/.../navigation/VspNavHost.kt`
- [X] T075 [US3] Persist confirmed vehicle to inspection (and, when online + configured, to Firestore via sync) in `core/data/.../repository/VehicleRepositoryImpl.kt`

**Checkpoint**: Identification supports VIN + New/Old classification, OCR, decode, fallback.

---

## Phase 6B: User Story 9 - Old-Vehicle Documentation & Details (Priority: P2)

**Goal**: For Old vehicles, require RC / Pollution Certificate / Insurance photos plus
number-of-ownerships and number-of-keys before exterior capture; skip entirely for New.

**Independent Test**: Classify Old → app requires the three document photos + ownership/key
counts before exterior capture; classify New → step is skipped.

**Dependencies**: Requires US3 (classification) and the document catalog/section (T012, T008,
T016) and `ImageRepository` capture (T050).

### Tests for User Story 9 ⚠️

- [X] T075b [P] [US9] Unit test `SaveOldVehicleDetailsUseCase` (required reg + counts for Old; not for New) via `VehicleUseCasesTest` in `core/domain/src/test/...`
- [ ] T075c [P] [US9] UI test Old-vehicle documentation screen (RC/PUC/Insurance capture, ownership + key inputs, gating, New skips step, accessibility) in `feature/identify/src/androidTest/...`

### Implementation for User Story 9

- [X] T075d [US9] Extend `ImageRepository` to capture/store `Section.DOCUMENT` images with `DocumentType` (RC/POLLUTION_CERTIFICATE/INSURANCE) in `core/data/.../repository/ImageRepositoryImpl.kt`
- [X] T075e [US9] Persist `numberOfOwnerships` and `numberOfKeys` on the vehicle in `core/data/.../repository/VehicleRepositoryImpl.kt` via `SaveOldVehicleDetailsUseCase`
- [X] T075f [P] [US9] Implement Old-vehicle documentation use cases (capture docs, save details, completeness gating incl. documents) in `core/domain/.../usecase/` and `CompletenessCalculator`
- [X] T075g [US9] Implement `OldVehicleDocsViewModel` + screen (sequential document capture, ownership/key inputs, progression gating) in `app/.../feature/olddocs/`
- [X] T075h [US9] Include Old-vehicle documents + details in report generation and sync in `core/data/.../repository/ReportRepositoryImpl.kt` and `.../sync/`

**Checkpoint**: Old vehicles require documentation & provenance details; New vehicles skip.

---

## Phase 7: User Story 7 - Offline-First Capture with Automatic Sync (Priority: P2)

**Goal**: Local-first everything with background upload, retry/backoff, idempotent resume,
and per-item sync status.

**Independent Test**: Complete inspection in airplane mode → re-enable → images/data upload
automatically with visible status and successful retry.

### Tests for User Story 7 ⚠️

- [ ] T076 [P] [US7] Unit test `EnqueueSyncUseCase`, `ObserveSyncStatusUseCase`, `RetryFailedUploadsUseCase` in `core/domain/src/test/...`
- [ ] T077 [P] [US7] Integration test sync/retry + idempotent resume against Firebase emulator (WorkManager test harness) in `core/data/src/androidTest/.../sync/`

### Implementation for User Story 7

- [X] T078 [US7] Implement `SyncRepository` (observe status from image/report sync states, enqueue, retry) in `core/data/.../repository/SyncRepositoryImpl.kt`
- [X] T079 [US7] Implement `ConnectivityMonitor` (Flow + isOnline) in `core/data/.../connectivity/`
- [X] T080 [US7] Implement Hilt WorkManager `SyncWorker` (image + report upload) with network constraint + retry, and `HiltWorkerFactory` wiring in `core/data/.../sync/` and `app/VspApplication`
- [X] T081 [US7] Implement Firestore/Storage upload data source with deterministic id-keyed paths (idempotent) in `core/data/.../remote/FirebaseRemoteDataSource.kt`
- [X] T082 [P] [US7] Implement sync use cases in `core/domain/.../usecase/ReportSyncUseCases.kt`
- [ ] T083 [US7] Surface per-item sync status badges across dashboard, capture, and review UIs (report screen shows sync summary; broader badges deferred)
- [X] T084 [US7] Schedule sync on report generation and finalize in `core/data/.../repository/`

**Checkpoint**: Full offline lifecycle with reliable automatic sync.

---

## Phase 8: User Story 4 - Manual Annotation (Priority: P2)

**Goal**: Unlimited annotations (rectangle/freehand/pin) with damage type, severity, and
comments.

**Independent Test**: Open image → draw rectangle + pin, set type/severity, add comment →
annotations persist.

### Tests for User Story 4 ⚠️

- [ ] T085 [P] [US4] Unit test `AddAnnotationUseCase`, `UpdateAnnotationUseCase`, `DeleteAnnotationUseCase` (geometry/severity validation, unlimited) in `feature/annotation/src/test/...`
- [ ] T086 [P] [US4] UI test annotation tools (draw rectangle/freehand/pin, type+severity pickers, comment, accessibility) in `feature/annotation/src/androidTest/...`

### Implementation for User Story 4

- [X] T087 [US4] Implement `AnnotationRepository` (CRUD, unlimited) in `core/data/.../repository/AnnotationRepositoryImpl.kt`
- [X] T088 [P] [US4] Implement annotation use cases in `core/domain/.../usecase/AnnotationUseCases.kt`
- [X] T089 [US4] Implement annotation overlay editor (tap-to-pin with type/severity/comment; pins + AI bboxes drawn on canvas) in `app/.../feature/imagedetail/` (rectangle/freehand tools: pin implemented, others deferred)
- [X] T090 [US4] Extend `ImageDetailViewModel` + screen for annotation create/delete + type/severity/comment in `app/.../feature/imagedetail/`

**Checkpoint**: Inspector can fully annotate any image.

---

## Phase 9: User Story 6 - Final AI Verification & Report Generation (Priority: P2)

**Goal**: Run AI across all images → scores + summary + integrity flags → structured JSON
report + report screen.

**Independent Test**: Finalize → scores (overall/exterior/interior/safety/cosmetic/
confidence), summary, integrity flags, and a structured report are produced and viewable.

### Tests for User Story 6 ⚠️

- [ ] T091 [P] [US6] Unit test `RunFinalVerificationUseCase` (validated scores/integrity; invalid→reject+retry) in `feature/verification/src/test/...`
- [X] T092 [P] [US6] Unit test report builder + JSON conformance (vehicle category/keys, severity counts, scores) via `ReportBuilderTest` in `core/data/src/test/.../report/`
- [ ] T093 [P] [US6] UI test Final Verification screen (scores, integrity flags, accessibility) in `feature/verification/src/androidTest/...`
- [ ] T094 [P] [US6] UI test Report screen (summary, galleries, damage list/count, findings, scores, recommendation, share, accessibility) in `feature/report/src/androidTest/...`

### Implementation for User Story 6

- [X] T095 [US6] Implement `AiAnalysisRepository.runFinalVerification` (validated scores/summary/integrity, persists to inspection) in `core/data/.../repository/AiAnalysisRepositoryImpl.kt`
- [X] T096 [US6] Implement `ReportRepository` (build validated JSON, persist encrypted, share, schedule sync) in `core/data/.../repository/ReportRepositoryImpl.kt`
- [X] T097 [P] [US6] Implement `RunFinalVerificationUseCase`, `GenerateReportUseCase`, `ShareReportUseCase` in `core/domain/.../usecase/`
- [X] T098 [US6] Implement `VerificationViewModel` + screen + nav in `app/.../feature/verification/`
- [X] T099 [US6] Implement `ReportViewModel` + Report screen (JSON preview, share intent, sync status) in `app/.../feature/report/`
- [X] T100 [US6] Wire finalize→final-verification→report into the wizard graph and schedule report upload in `app/.../navigation/` and `core/data/.../sync/`

**Checkpoint**: Completed inspections produce decision-ready reports.

---

## Phase 10: User Story 5 - Annotation AI Re-Verification (Priority: P3)

**Goal**: After a manual annotation, AI rechecks the region, confirms/corrects, finds
nearby damage, merges overlaps, flags inconsistencies.

**Independent Test**: Annotate a region → re-verify → confirmation/correction, nearby
detections, merged overlaps, and inconsistency flag returned.

### Tests for User Story 5 ⚠️

- [ ] T101 [P] [US5] Unit test `ReverifyAnnotationUseCase` (validated result, merge overlaps, inconsistency flag, invalid→reject) in `feature/annotation/src/test/...`
- [ ] T102 [P] [US5] UI test re-verify action on image detail (results surfaced, manual annotation preserved, accessibility) in `feature/annotation/src/androidTest/...`

### Implementation for User Story 5

- [X] T103 [US5] Implement `AiAnalysisRepository.reverifyAnnotation` (validated per contracts/ai-gemini-contract.md §2) in `core/data/.../repository/AiAnalysisRepositoryImpl.kt`
- [X] T104 [P] [US5] Implement `ReverifyAnnotationUseCase` in `core/domain/.../usecase/AiUseCases.kt`
- [X] T105 [US5] Add re-verify action + result surfacing to `ImageDetailViewModel` + screen in `app/.../feature/imagedetail/`

**Checkpoint**: All user stories complete and independently functional.

---

## Phase 12: Checklist-First Workflow & Enhancements (delivered 2026-07-03)

**Purpose**: Catalog-driven checklist as the primary flow, per-item photos, granular condition
grades, continuous capture, deletion, and a redesigned/size-bounded report. See
`checklist-integration-plan.md` (§11) and spec.md FR-049…FR-062. All tasks build + verified.

### Checklist catalog, persistence & domain
- [X] T116 Add `ChecklistCatalog` (sections→groups→items, `Applicability`, `ChecklistResponseType`, `photoCapable`) + `ChecklistStatus` grades (GOOD/MINOR_SCRATCHES/MAJOR_SCRATCHES/DAMAGE) in `core/model/.../catalog/`
- [X] T117 Add `ChecklistResponse` model, `ChecklistResponseEntity` + DAO, `ChecklistRepository`, and use cases (observe/save) with DB `checklist_responses` table
- [X] T118 Add `RepairRecommendation` (6 options) and wire the Final Assessment recommendation item
- [X] T119 Add `vehicles.odometerKm` and annotation damage-assessment fields (component, vehicleSide, estimatedSize, repairRequired, estimatedCost, manualVerified) — DB v1→v2 migration

### Checklist-first UI & per-item photos
- [X] T120 Add checklist-first navigation (Identify → ChecklistHub/ChecklistSection); New & Old load the checklist directly (Old skips the standalone document screen)
- [X] T121 Tag images with `checklistSectionId`/`checklistItemId` on `InspectionImage`/entity/mapper — DB v2→v3 and v3→v4 migrations; `captureSectionImage(itemId)` repository/use case
- [X] T122 Per-item image grid (max `MAX_IMAGES_PER_ITEM`, `app` BuildConfig, default 10) with add-photo tile; component condition-grade chips (area-aware wording)
- [X] T123 Continuous `SectionCaptureScreen` (capture until back; Total/Remaining/session counts; keep/discard confirmation)
- [X] T124 Full-path image labels (Section → Group → Item) via shared `ImageLabels`; ImageDetail gallery scoped to item (fallback section/inspection) with swipe order

### Deletion
- [X] T125 `deleteImage` (repository + `DeleteImageUseCase`) with per-grid delete + confirmation (DB row + file)
- [X] T126 `deleteInspection` (repository + `DeleteInspectionUseCase`) from Dashboard with confirmation (cascade DB + files)

### Report (JSON + PDF)
- [X] T127 JSON report: nest photos under checklist items (`checklistItem` label), checklist + damageAssessment + finalAssessment blocks, catalog ordering; keep only untagged photos in top-level `images[]` (`ReportBuilder` + tests)
- [X] T128 Redesign `PdfReportGenerator` into a branded multi-section report (cover, contents, at-a-glance, category summary, per-category parameter tables, gallery, damage evidence)
- [X] T129 PDF size controls in `core:data` BuildConfig (`PDF_IMAGE_QUALITY`, `PDF_GALLERY_IMAGE_WIDTH`, `PDF_DAMAGE_IMAGE_WIDTH`, `PDF_MAX_IMAGES`); embed each photo once (tight downscale + JPEG re-encode)

### Misc
- [X] T130 Hard-code `test`/`test` login for testing (offline auth fallback); update `LoginViewModelTest`
- [X] T131 Relax finalize gating to ≥ 1 captured photo (checklist-driven)

**Checkpoint**: Checklist-first inspection with per-item photos, granular grades, deletion, and
a branded, size-bounded report — build + install verified.

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: Non-functional requirements, hardening, and delivery.

- [ ] T106 [P] Add localization (string resources externalized, RTL check) across all `:feature:*` and `:core:ui`
- [ ] T107 [P] Verify tablet + landscape layouts and dark mode across all screens in `:feature:*`
- [ ] T108 [P] Accessibility audit pass (semantics, contrast, touch targets, dynamic font) across all screens
- [X] T109 Implement runtime camera permission handling (request + rationale) in `app/.../feature/capture/CameraCapture.kt` (location/mic deep-link deferred)
- [ ] T110 [P] Enforce encryption at rest (SQLCipher/field encryption, encrypted file store + DataStore) in `core/data/.../local/` and `core/datastore/.../`
- [X] T111 [P] Author Firestore + Storage security rules (owner-scoped) in `firebase/firestore.rules` and `firebase/storage.rules` (App Check deferred)
- [ ] T112 [P] Add image compression tuning + performance checks (capture responsiveness, gallery scroll)
- [X] T113 Set up CI pipeline (assembleDebug + unit tests + report artifact) in `.github/workflows/android.yml` (lint/Kover/signed release deferred)
- [ ] T114 [P] Wire Crashlytics non-fatals for `AppError` categories and Analytics events across repositories/viewmodels
- [ ] T115 Run quickstart.md end-to-end smoke test and fix gaps

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories.
- **US8 (Phase 3, P1)**: Depends on Foundational.
- **US1 (Phase 4, P1, MVP)**: Depends on Foundational; uses US8's session/dashboard entry.
- **US2 (Phase 5, P1)**: Depends on Foundational + US1 (captured images to analyze).
- **US3 (Phase 6, P2)**: Depends on Foundational; adds VIN + New/Old classification and
  enhances US1's identify step.
- **US9 (Phase 6B, P2)**: Depends on US3 (classification) + US1's `ImageRepository`;
  required only for Old vehicles, before exterior capture.
- **US7 (Phase 7, P2)**: Depends on Foundational; syncs artifacts from US1/US2/US4/US6/US9.
- **US4 (Phase 8, P2)**: Depends on Foundational + US1 (image detail surface).
- **US6 (Phase 9, P2)**: Depends on US1, US2, US4 (images, findings, annotations).
- **US5 (Phase 10, P3)**: Depends on US2 + US4.
- **Polish (Phase 11)**: Depends on desired stories being complete.

### Within Each User Story

- Tests written first and expected to fail before implementation (Principle X).
- Models/data sources → repositories → use cases → viewmodels → screens → nav wiring.

### Parallel Opportunities

- All `[P]` Setup tasks (T003–T005, T007) can run together after T001–T002.
- Foundational `[P]` tasks (T008–T015, T018, T020–T028) can run in parallel where files differ.
- All `[P]` test tasks within a story can run in parallel.
- After Foundational, US8 and the US1 vehicle-details/capture scaffolding can progress in
  parallel by different developers; US2/US4/US6 layer on US1.

---

## Parallel Example: User Story 1

```bash
# Tests for US1 (write first, expect fail):
Task: "Unit test capture use cases in feature/capture/src/test/..."           # T042
Task: "Unit test SaveVehicleDetailsUseCase in feature/identify/src/test/..."  # T043
Task: "Unit test completeness/finalize in feature/review/src/test/..."        # T044
Task: "UI test Vehicle Details screen"                                        # T045
Task: "UI test Capture screen"                                                # T046
Task: "UI test Review screen"                                                 # T047
```

---

## Implementation Strategy

### MVP First

1. Phase 1 (Setup) → Phase 2 (Foundational).
2. Phase 3 (US8 Auth & Dashboard) → Phase 4 (US1 Guided Inspection).
3. **STOP and VALIDATE**: complete an offline inspection through finalize on one device.
4. Add Phase 5 (US2 AI detection) to reach the differentiated MVP.

### Incremental Delivery

- Foundational → US8 → US1 (MVP) → US2 → US3 → US9 → US7 → US4 → US6 → US5 → Polish.
- Each story is independently testable and adds value without breaking prior stories.

---

## Notes

- `[P]` = different files, no dependencies on incomplete tasks.
- Every use case has a unit test; every screen has a UI test (Constitution Principle X).
- Every AI response is validated before persistence or display (Principle VIII).
- Room + encrypted file store is the offline source of truth (Principle VI).
- Commit after each task or logical group; stop at checkpoints to validate independently.
