# Phase 0 Research: Android Vehicle Inspection Application

**Feature**: `001-vehicle-inspection-app` | **Date**: 2026-07-01

This document records the technical decisions resolving the Technical Context. The stack
was largely fixed by the user and the project constitution; research focuses on the best
way to apply each choice and on resolving the few open questions.

## Resolved unknowns

No `NEEDS CLARIFICATION` markers remained from the spec. Two implementation-level
questions were resolved below (VIN decode source, on-device quality validation approach).

---

## Decisions

### D1. Modularization strategy

- **Decision**: Multi-module Gradle (Kotlin DSL) with `:app`, `:core:*` (model, common,
  domain, data, datastore, camera, ui, testing) and `:feature:*` modules; convention
  plugins in `build-logic`.
- **Rationale**: Satisfies Constitution Principles III (Clean Architecture) and IV
  (feature-based, SOLID). Pure-Kotlin `:core:model`/`:core:domain` enforce inward
  dependencies at compile time. Feature modules enable parallel work and isolated tests.
- **Alternatives considered**: Single-module with packages (rejected: cannot enforce layer
  boundaries at compile time, weaker modularity); per-layer top-level modules only
  (rejected: not feature-based, poor scalability).

### D2. Presentation pattern & state

- **Decision**: MVVM with unidirectional data flow; one immutable `UiState` per screen
  exposed as `StateFlow`; `UiEvent` intents into the ViewModel; `SavedStateHandle` for args.
- **Rationale**: Principle II (single immutable state per screen) and I (Flow). Predictable,
  testable, survives config changes; Room persistence survives process death.
- **Alternatives considered**: MVI libraries (rejected: extra dependency, hand-rolled UDF
  is sufficient and lighter); LiveData (rejected: Flow-first per Principle I).

### D3. Navigation

- **Decision**: Navigation Compose, single-activity, nested graphs; type-safe routes; each
  feature exposes a `NavGraphBuilder` extension; inspection is a resumable linear wizard
  with a deep-link `inspection/{id}` to resume at the saved step.
- **Rationale**: First-class Compose integration; wizard maps cleanly to a nested graph;
  resume-from-dashboard requires durable step state (stored in Room).
- **Alternatives considered**: Multiple activities (rejected: heavier, worse shared state);
  third-party nav (rejected: unnecessary given Navigation Compose maturity).

### D4. Local persistence & source of truth

- **Decision**: Room as structured source of truth; images/thumbnails in app-internal
  encrypted file storage referenced from Room; DataStore for session/preferences.
- **Rationale**: Principle VI (offline-first). Storing large binaries as files (not BLOBs)
  keeps DB fast; Room DAOs expose `Flow` for reactive UI.
- **Alternatives considered**: BLOBs in Room (rejected: DB bloat/perf); direct Firestore
  reads (rejected: violates offline-first).

### D5. Firebase integration boundary

- **Decision**: Firestore + Storage + Auth + Crashlytics + Analytics + App Check, all
  confined to `:core:data` behind repository ports; SDK types never leak outward.
- **Rationale**: Principles VII and III. Keeps domain/presentation vendor-agnostic and
  testable with fakes/emulator.
- **Alternatives considered**: Custom backend (rejected: user specified Firebase); exposing
  Firestore models to UI (rejected: violates layering).

### D6. Gemini Vision integration & validation

- **Decision**: Gemini Vision accessed through a domain `AiVisionPort`; the data-layer
  implementation sends the image + a structured prompt requesting JSON, then **validates
  every response** against a strict schema (required fields, enum damage types, confidence
  0–1, severity enum, bounding-box bounds) via kotlinx.serialization + explicit validators
  before mapping to domain findings.
- **Rationale**: Principle VIII (all AI responses validated before use). Structured-output
  prompting + schema validation prevents malformed/hallucinated data from being persisted
  or shown as authoritative; low-confidence flagged for review.
- **Alternatives considered**: Trusting model text output directly (rejected: violates
  Principle VIII); on-device-only ML damage model (rejected: user specified Gemini; kept as
  future enhancement).

### D7. AI when offline

- **Decision**: Analysis is queued when offline (`AiAnalysisWorker`, WorkManager,
  `NetworkType.CONNECTED`); capture never blocks; inspection can be finalized with findings
  marked pending; final verification runs when the image set + connectivity are available.
- **Rationale**: Principle VI (offline-first) with the reality that Gemini needs network.
- **Alternatives considered**: Blocking capture on analysis (rejected: breaks offline UX).

### D8. Camera & image quality validation

- **Decision**: CameraX for capture with a guided overlay (example, outline, angle,
  distance). On-device quality validation for blur (Laplacian variance), brightness
  (luminance histogram for dark/overexposed), and completeness (subject/edge heuristics);
  reject with a specific reason and prompt recapture.
- **Rationale**: FR-018 requires on-device rejection; CameraX gives lifecycle-safe capture.
  Lightweight image metrics run fast (< ~300 ms) without a network call.
- **Alternatives considered**: Cloud-side quality checks (rejected: needs network, slow,
  violates offline capture); ML Kit image labeling for completeness (kept optional as an
  enhancement).

### D9. VIN capture & decode

- **Decision**: VIN OCR via ML Kit Text Recognition from a CameraX frame, with VIN-pattern
  extraction/validation (17-char, check-digit) and inspector confirmation. VIN **decode**
  uses a pluggable `VinDecodeSource` behind `VehicleRepository`; on failure or missing
  fields, fall back to manual attribute selection. Barcode/QR is a future feature.
- **Rationale**: FR-009/010/013. Abstracting the decode source keeps the app independent of
  any specific VIN provider and testable; OCR + validation reduces bad input.
- **Alternatives considered**: Hard-coding a single VIN API (rejected: coupling, may be
  unavailable per market); on-device VIN database (rejected: large/stale; out of scope).

### D10. Background sync & upload

- **Decision**: WorkManager (Hilt-integrated) with a Room-backed `SyncQueue`; unique
  ordered work chains per inspection (images → metadata → AI results → report); exponential
  backoff; idempotent uploads via deterministic Storage paths + Firestore doc ids keyed by
  local ids; per-item `syncState` observable in UI.
- **Rationale**: FR-039/041; survives app kill, retries, resumes without duplicates.
- **Alternatives considered**: Foreground services (rejected: heavier, worse battery);
  ad-hoc coroutine uploads (rejected: no guaranteed execution/retry across process death).

### D11. Report generation & format

- **Decision**: Canonical report is JSON (kotlinx.serialization) built from Room data;
  stored locally, uploaded to Storage, and referenced by a Firestore report doc; the report
  screen renders from the same structured model. Share/export derived from JSON (PDF export
  is a future enhancement).
- **Rationale**: FR-035/036/037; single canonical structure avoids divergence.
- **Alternatives considered**: PDF-first (rejected: harder to validate/query; deferred).

### D12. Error model

- **Decision**: Typed `AppError` sealed hierarchy in `:core:common`; use cases return
  `Result<T, AppError>`; repositories map data-source exceptions to `AppError`; VIN failure
  and image-quality rejection are modeled as domain outcomes, not crashes.
- **Rationale**: Predictable, testable error handling across layers (Principles III, X).
- **Alternatives considered**: Throwing exceptions across layers (rejected: harder to test
  and reason about).

### D13. Dependency injection

- **Decision**: Hilt across all modules; dispatchers, repositories, data sources, and AI
  clients bound to interfaces; WorkManager Hilt integration for workers.
- **Rationale**: Principle V; enables test doubles and swappable implementations.
- **Alternatives considered**: Koin/manual DI (rejected: constitution mandates Hilt).

### D14. Security posture

- **Decision**: Encrypt sensitive data at rest (SQLCipher/field encryption, encrypted file
  store, encrypted DataStore); TLS transport; Firebase Security Rules scoped to owning
  inspector; App Check; secrets injected via CI (never committed); validate all inputs.
- **Rationale**: FR-048 and constitution security gate.
- **Alternatives considered**: Plaintext local storage (rejected: sensitive records).

### D15. Testing tooling

- **Decision**: JUnit4 + MockK + Turbine + coroutines-test for unit; Compose UI Test +
  Hilt test rules + Espresso for UI; Firebase emulator + WorkManager test harness for
  integration; Kover for coverage; fakes in `:core:testing`.
- **Rationale**: Principle X (unit per use case, UI per screen) + accessibility assertions.
- **Alternatives considered**: Robolectric-only UI (kept where feasible for speed, but
  instrumented tests required for real screen behavior).

## Best-practice notes applied

- Compose: hoist state, immutable state objects, `collectAsStateWithLifecycle`, stable keys.
- Coroutines: inject dispatchers, structured concurrency, cancel on scope end.
- CameraX: bind to lifecycle, use `ImageCapture` + analysis use cases judiciously.
- Room: `Flow` return types, indices on FKs and query columns, migrations from day one.
- WorkManager: unique work, constraints, backoff, idempotent operations.
- Firebase: security rules first, App Check, emulator for tests, offline persistence off in
  favor of app-owned offline model to keep a single source of truth.

## Open items deferred to implementation

- Exact confidence threshold value for `reviewRequired` (configurable; sensible default).
- Concrete VIN decode provider selection (abstracted behind `VinDecodeSource`).
- PDF export, barcode/QR VIN, voice comments, multi-inspector (future enhancements).
