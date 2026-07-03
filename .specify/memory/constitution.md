<!--
SYNC IMPACT REPORT
==================
Version change: (uninitialized template) → 1.0.0
Bump rationale: Initial ratification of the project constitution (MAJOR baseline).

Modified principles: N/A (initial adoption)

Added principles:
  - I. Kotlin-First & Structured Concurrency
  - II. Jetpack Compose + Material 3 UI
  - III. Layered Architecture (MVVM + Clean Architecture + Repository Pattern)
  - IV. Modular, Feature-Based & SOLID Design
  - V. Dependency Injection with Hilt
  - VI. Offline-First Data
  - VII. Firebase Backend Integration
  - VIII. Validated Gemini Vision AI
  - IX. Accessibility Compliance
  - X. Test Discipline (NON-NEGOTIABLE)

Added sections:
  - Technology & Platform Constraints
  - Development Workflow & Quality Gates

Removed sections: None

Templates requiring updates:
  - ✅ .specify/templates/plan-template.md (Constitution Check gate references this file; no structural edit required)
  - ✅ .specify/templates/spec-template.md (no changes required; remains technology-agnostic)
  - ✅ .specify/templates/tasks-template.md (test-task guidance compatible with Principle X; no structural edit required)

Follow-up TODOs: None
-->

# Vehicle Inspection System Constitution

The Vehicle Inspection System is a native Android application for capturing, analyzing,
and reporting vehicle inspections, including AI-assisted visual damage and condition
assessment. This constitution defines the non-negotiable engineering principles that
govern its design, implementation, and evolution.

## Core Principles

### I. Kotlin-First & Structured Concurrency

- All application, test, and build-logic code MUST be written in Kotlin. Java source
  files MUST NOT be added to the codebase.
- All asynchronous and background work MUST use Kotlin Coroutines. Blocking calls,
  raw threads, `AsyncTask`, and callback-based concurrency for app logic are prohibited.
- Streams of state and data MUST be modeled with Kotlin `Flow` (including `StateFlow`
  and `SharedFlow`). ViewModels MUST expose UI state as observable `Flow`/`StateFlow`,
  not as mutable public fields.

**Rationale**: A single modern language plus structured concurrency yields predictable
cancellation, lifecycle safety, and testable reactive state.

### II. Jetpack Compose + Material 3 UI

- All user interfaces MUST be built with Jetpack Compose. The Android View/XML layout
  system MUST NOT be used for new screens.
- All UI components MUST use Material 3 (`androidx.compose.material3`) design tokens,
  theming, and components. Material 2 components MUST NOT be introduced.
- UI state MUST be driven by a single immutable state object per screen, sourced from a
  ViewModel `StateFlow`.

**Rationale**: A unified declarative UI stack with one design system keeps screens
consistent, testable, and maintainable.

### III. Layered Architecture (MVVM + Clean Architecture + Repository Pattern)

- The codebase MUST follow Clean Architecture with explicit `presentation`, `domain`,
  and `data` layers. Dependencies MUST point inward: `presentation → domain ← data`.
  The `domain` layer MUST NOT depend on Android framework or data-source libraries.
- The presentation layer MUST follow MVVM: Composables render state; ViewModels hold
  state and delegate business logic to use cases. Business logic MUST NOT live in
  Composables or Activities/Fragments.
- Domain logic MUST be expressed as single-responsibility use cases (interactors).
- All data access MUST go through repository interfaces defined in the `domain` layer
  and implemented in the `data` layer. ViewModels and use cases MUST NOT access data
  sources (Room, Firebase, network, Gemini) directly.

**Rationale**: Enforced layering and the repository boundary keep business rules
independent of frameworks and data sources, enabling substitution and isolated testing.

### IV. Modular, Feature-Based & SOLID Design

- Code MUST be organized into feature-based packages/modules; each feature owns its
  presentation, domain, and data code. Organizing by technical type across features
  (a single global `viewmodels/` or `models/` package) is prohibited.
- Modules MUST be independently buildable and MUST NOT introduce cyclic dependencies.
- All code MUST adhere to SOLID principles. In particular, classes MUST have a single
  responsibility and MUST depend on abstractions (interfaces) rather than concrete
  implementations across layer boundaries.

**Rationale**: Feature modularity plus SOLID limits blast radius of change, enables
parallel work, and keeps components replaceable and testable.

### V. Dependency Injection with Hilt

- All dependency wiring MUST use Hilt. Manual singletons, service locators, and ad-hoc
  `object` graphs for injectable dependencies are prohibited.
- Cross-boundary dependencies (repositories, data sources, AI clients, dispatchers)
  MUST be provided via Hilt bindings against interfaces so they can be replaced in tests.
- Coroutine dispatchers MUST be injected (not hard-coded) to keep code testable.

**Rationale**: Centralized, compile-time-verified DI makes dependencies explicit and
swappable, which is a prerequisite for testability and modularity.

### VI. Offline-First Data

- The application MUST be fully usable for core inspection workflows without network
  connectivity. Reads MUST be served from a local source of truth (e.g., Room).
- Writes made offline MUST be persisted locally and synchronized to the backend when
  connectivity is restored, with conflict handling defined per data type.
- UI MUST NOT block on network availability for core capture and review flows, and MUST
  clearly reflect sync state (pending, synced, failed).

**Rationale**: Inspections happen in garages, lots, and remote sites where connectivity
is unreliable; local-first storage guarantees the app remains functional.

### VII. Firebase Backend Integration

- The remote backend MUST be Firebase (e.g., Authentication, Firestore/Realtime
  Database, Storage, and related services as needed).
- All Firebase access MUST be encapsulated behind repository interfaces in the `data`
  layer; Firebase SDK types MUST NOT leak into the `domain` or `presentation` layers.
- Remote operations MUST integrate with the offline-first sync strategy defined in
  Principle VI rather than being invoked directly from UI.

**Rationale**: A single managed backend behind repository boundaries keeps the domain
independent of vendor SDKs and consistent with the offline-first model.

### VIII. Validated Gemini Vision AI

- Visual inspection analysis MUST use Gemini Vision AI, accessed through a domain-level
  abstraction (interface) implemented in the `data` layer.
- Every AI response MUST be validated before use: outputs MUST be parsed into strongly
  typed domain models and checked against schema, value ranges, and required fields.
  Raw or unvalidated AI output MUST NOT be persisted, displayed as authoritative, or
  used to drive irreversible actions.
- AI failures, low-confidence results, and validation failures MUST be handled
  gracefully with safe fallbacks and clear user messaging; the app MUST never crash or
  present invalid AI data as verified fact.

**Rationale**: AI output is probabilistic; mandatory validation protects data integrity,
user trust, and the correctness of inspection records.

### IX. Accessibility Compliance

- All screens and interactive components MUST be accessible: meaningful content
  descriptions/semantics, correct focus order, and support for TalkBack.
- UI MUST meet baseline accessibility standards, including sufficient color contrast,
  adequate touch target sizes, and support for dynamic font scaling.
- Accessibility MUST be verified as part of UI testing (see Principle X); non-accessible
  UI is treated as incomplete.

**Rationale**: Inspections are performed by diverse users in the field; accessibility is
a correctness and compliance requirement, not an enhancement.

### X. Test Discipline (NON-NEGOTIABLE)

- All code MUST be testable by design: logic depends on injected abstractions
  (Principle V) and pure domain models, avoiding hidden global state and framework
  coupling.
- Every use case MUST have unit tests covering its success paths, failure paths, and
  boundary conditions.
- Every screen MUST have UI (instrumentation) tests covering its primary interactions
  and state rendering, including accessibility semantics.
- A change that adds or modifies a use case or screen without corresponding tests MUST
  NOT be merged.

**Rationale**: Guaranteed unit coverage for business rules and UI coverage for every
screen keeps the app correct and safe to change.

## Technology & Platform Constraints

- **Language**: Kotlin (exclusively).
- **Minimum SDK**: API level 26 (Android 8.0). Features MUST support `minSdk = 26`;
  usage of newer APIs MUST be gated appropriately and MUST NOT break API 26 devices.
- **UI Toolkit**: Jetpack Compose with Material 3.
- **Concurrency**: Kotlin Coroutines and `Flow`.
- **Dependency Injection**: Hilt.
- **Local Storage**: A local persistence layer (e.g., Room) as the offline source of truth.
- **Backend**: Firebase.
- **AI**: Gemini Vision AI, accessed via a validated domain abstraction.
- **Architecture**: MVVM + Clean Architecture with the Repository Pattern, organized
  into feature-based modules/packages.

## Development Workflow & Quality Gates

- **Constitution Check**: Every implementation plan MUST pass a Constitution Check gate
  confirming alignment with all Core Principles before design and again before merge.
- **Layering enforcement**: Reviewers MUST reject changes that violate the dependency
  direction in Principle III or that leak framework/vendor types across layer boundaries.
- **Testing gate**: CI MUST run unit and UI tests. Merges are blocked when required
  tests (Principle X) are missing or failing.
- **Accessibility gate**: UI changes MUST include accessibility verification per
  Principle IX before merge.
- **AI safety gate**: Any change touching AI integration MUST demonstrate response
  validation per Principle VIII.
- **Code review**: All changes MUST be reviewed; reviewers MUST verify SOLID adherence,
  feature modularity, and testability. Complexity that deviates from these principles
  MUST be explicitly justified in the plan's Complexity Tracking section.

## Governance

- This constitution supersedes all other development practices for the Vehicle
  Inspection System. Where guidance conflicts, this document prevails.
- **Amendments**: Changes MUST be proposed via pull request, documented with rationale,
  reviewed and approved by project maintainers, and accompanied by a migration/impact
  note for any affected code or templates.
- **Versioning Policy**: This constitution is versioned using semantic versioning:
  - **MAJOR**: Backward-incompatible governance changes or removal/redefinition of a
    principle.
  - **MINOR**: Addition of a new principle/section or materially expanded guidance.
  - **PATCH**: Clarifications, wording, and non-semantic refinements.
- **Compliance Review**: Every plan, review, and PR MUST verify compliance with these
  principles. Non-compliant work MUST be corrected or explicitly justified before merge.
- **Runtime Guidance**: Use the applicable Spec Kit templates under `.specify/templates/`
  for plan, spec, and task generation; these MUST remain consistent with this constitution.

**Version**: 1.0.0 | **Ratified**: 2026-07-01 | **Last Amended**: 2026-07-01
