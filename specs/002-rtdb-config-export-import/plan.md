# Implementation Plan: Configurable Checklist (Firebase RTDB), Custom Auth, and Zip Export/Import

**Feature Branch**: `002-rtdb-config-export-import` | **Date**: 2026-07-04
**Depends on**: `001-vehicle-inspection-app` (existing app)
**Status**: Draft — pending confirmation of open items (see §2)

---

## 1. Summary

Evolve the app from a **hardcoded, compile-time checklist** to a **vendor-configurable, Firebase
Realtime Database (RTDB)-driven** configuration model, add **account creation + login backed by a
custom user model in RTDB**, and add **offline device-migration via zip export/import**.

Scope of RTDB is deliberately narrow (confirmed): RTDB stores **only configuration and auth data** —
users, vehicle make/model/variant catalog, and the inspection questionnaire (sections, questions,
options, and per-question capture rules). **Captured inspection data never goes to the cloud**; it
stays in the local Room + encrypted file store and is transferred between devices only through the
zip export/import feature.

Three invariants drive the design:

1. **Config changes must not corrupt in-flight work.** Each inspection pins ("snapshots") the exact
   questionnaire definition it was created with. Firebase edits never mutate existing inspections.
2. **Re-sync is gated.** The app adopts a new questionnaire version only on **logout → re-login**, or
   when **no local inspection data exists**.
3. **Import is integrity-checked.** A zip may only be imported when its embedded questionnaire
   version/hash and its CSV columns exactly match the app's current active questionnaire. Any mismatch
   hard-blocks the import.

---

## 2. Open items (need input before build)

| # | Item | Status |
|---|------|--------|
| O-1 | **Firebase RTDB URL.** | ✅ **Resolved** — `https://readywire-vehicle-inspection-f9a02.firebaseio.com/` (ReadyWire vendor). Configured via `local.properties` → `BuildConfig.FIREBASE_DB_URL` (§17). Kept out of source control. |
| O-2 | **Firebase project bootstrap without Firebase Auth.** Custom RTDB auth means RTDB security rules cannot use `request.auth`. | ✅ **Resolved (implemented)** — Anonymous Firebase Auth is used purely to satisfy `auth != null` in the RTDB rules; the custom credential model (PBKDF2, RTDB `/users`) rides on top. See `FirebaseInitializer.ensureAuth()` and `firebase/database.rules.json`. Enable **Anonymous** sign-in in the Firebase console. |
| O-3 | **Import conflict policy** when an inspection id already exists locally. | ✅ **Resolved (implemented)** — existing inspection ids are **skipped** (idempotent re-import). See `ImportRepositoryImpl`. |

The rest of this plan proceeds on the confirmed decisions from clarification (§3).

---

## Implementation status (delivered)

All phases are implemented and the project builds (`:app:assembleDebug` green; Room migrated to v5;
unit tests pass). Key deltas from the original design, chosen for robustness ("no issues"):

- **RTDB config storage** uses a versioned node carrying a serialized JSON payload
  (`/config/questionnaire = { version, hash, updatedAt, json }`) rather than an exploded tree. This
  keeps the content hash stable and parsing bullet-proof; an admin edits the `json` field + bumps
  `version` from the console. Same for `/config/vehicleCatalog`.
- **Baseline JSON asset** `app/src/main/assets/baseline_questionnaire.json` is generated from the
  in-code `BaselineQuestionnaire` (derived from `ChecklistCatalog`) via `BaselineAssetGeneratorTest`,
  and preferred at runtime by `BaselineProvider` (falls back to in-code). Vendors can edit it.
- **Per-question image config** (`allowImage`, `maxImages`) lives on every `ConfigItem` and is
  enforced at capture time via `GetItemImageLimitUseCase` reading the inspection's pinned snapshot.
- **Export bundle** carries, per inspection, a lossless `inspection.json` **and** a human-readable
  long-format `data.csv` (used for the CSV-match import validation) plus `images/`.
- **Firestore/Storage sync** (`SyncWorker`, `FirebaseRemoteDataSource`) is left in place but unused
  by the new flow (uses the default FirebaseApp); the vendor RTDB uses a dedicated named app. Full
  removal can follow once cloud image sync is formally retired.

Component map: config (`core/data/config/*`, `core/model/config/*`), RTDB
(`core/data/remote/rtdb/*`), auth (`AuthRepositoryImpl`, `security/PasswordHasher`), portability
(`core/data/portability/*`), UI (`feature/auth/SignUp*`, `feature/data/Data*`), rules
(`firebase/database.rules.json`).

---

## 3. Confirmed decisions (from clarification)

- **D-1 RTDB scope**: config + auth only; inspection data local + zip export/import only.
- **D-2 Auth**: custom user model stored in RTDB (no Firebase Authentication for credentials).
- **D-3 Versioning**: snapshot the full questionnaire definition + version id + content hash into each
  inspection at creation.
- **D-4 Import mismatch**: hard-block with a clear message.
- **D-5 Vendor URL**: build-time; one Firebase URL per vendor build via `local.properties` → `BuildConfig`
  (same pattern as `GEMINI_API_KEY`).
- **D-6 CSV**: one CSV per inspection, **long format** (one row per checklist item/answer), plus vehicle/meta rows.

---

## 4. Goals & non-goals

### Goals
- Vendor-configurable questionnaire (sections, groups, items, options) fetched from RTDB.
- Per-question configurability: **allow image (yes/no)** and **max images allowed** (replacing the single
  global `BuildConfig.MAX_IMAGES_PER_ITEM`).
- Make/model/variant static catalog in RTDB, consumed by vehicle identification.
- Custom account creation + login (RTDB), with offline re-login via a cached credential.
- Baseline questionnaire JSON bundled in `assets/`, auto-seeded to RTDB (and used locally) for
  first-time vendors with no existing configuration.
- Snapshot-based version pinning so config edits never affect existing inspections.
- Gated re-sync (logout/relogin or empty inspection set).
- Zip export of all user data (root → per-inspection subfolders → images + CSV), shareable.
- Zip import for device migration, with strict questionnaire + CSV compatibility validation.

### Non-goals (this feature)
- Cloud sync of captured inspection data (explicitly removed; see §14).
- Multi-inspector collaboration.
- Admin UI inside the app for editing the questionnaire (admins edit RTDB directly / via a console).
- Runtime vendor switching (URL is build-time per D-5).

---

## 5. Architecture overview

Reuse the existing Clean Architecture + multi-module layout. New responsibilities land in existing
layers so the domain stays framework-free.

```text
:core:model
   + catalog/QuestionnaireConfig.kt      (serializable config models; replaces static ChecklistCatalog usage)
   + catalog/VehicleCatalog.kt           (make/model/variant)
   + auth/AppUser.kt, Credentials

:core:domain (ports + use cases)
   + port/RemoteConfigSource            (RTDB reads: questionnaire, vehicle catalog)
   + port/RemoteUserSource              (RTDB custom auth: create, authenticate, fetch)
   + repository/ConfigRepository        (active/pinned questionnaire, sync gating)
   + repository/ExportRepository, ImportRepository
   + usecase/{SignUp, SignIn, SyncConfig, ExportData, ImportData, ValidateImport}UseCase

:core:data
   + remote/rtdb/FirebaseRtdbConfigSource.kt   (RTDB SDK confined here)
   + remote/rtdb/FirebaseRtdbUserSource.kt
   + config/ConfigRepositoryImpl.kt            (snapshot + gating logic)
   + config/BaselineCatalogSeeder.kt           (reads assets baseline JSON)
   + export/ZipExporter.kt, export/CsvWriter.kt
   + import/ZipImporter.kt, import/CsvReader.kt, import/ImportValidator.kt
   + local: new Room tables + migration v4 -> v5
   + di: RtdbModule, FirebaseAppInitializer (manual FirebaseOptions from BuildConfig)

:core:datastore
   + ConfigStore (active questionnaire version/hash pointer, last sync time)
   + credential cache for offline re-login (encrypted)

:feature/app
   + Signup screen; extend Login for account creation entry
   + Settings/Data screen: Export, Import, "Sync configuration" (gated) actions
   + Vehicle identify screen consumes make/model/variant catalog
```

Vendor DB connection is created from `BuildConfig` (§13): `FirebaseDatabase.getInstance(app, FIREBASE_DB_URL)`
where `app` is a `FirebaseApp` initialized from manual `FirebaseOptions` (so `google-services.json` is
optional and the URL is fully vendor-controlled).

---

## 6. RTDB data model

All config is versioned. `hash` is a stable SHA-256 over the canonical (sorted-key) JSON of the
definition; it is the single source of truth for compatibility checks.

```text
/config
  /questionnaire
      version: 7                      # monotonic int, bumped on any change
      updatedAt: 1751600000000
      hash: "sha256:ab12…"            # canonical hash of sections tree
      sections:
        {sectionId}:
          id, title, order, appliesTo (NEW|OLD|BOTH)
          groups:
            {groupId}:
              id, title, order
              items:
                {itemId}:
                  id, label, order
                  responseType (STATUS_OK|YES_NO|PASS_FAIL|RATING_1_5|TEXT|NUMBER|COMPONENT)
                  appliesTo, unit?, mandatory
                  allowImage: true|false        # NEW: per-question image capability
                  maxImages: 10                  # NEW: per-question cap (0 when allowImage=false)
                  options: [ {value,label,order}, … ]   # for choice-type questions
  /vehicleCatalog
      version, updatedAt, hash
      makes:
        {makeId}: { name, order,
          models: { {modelId}: { name, order,
            variants: { {variantId}: { name, fuelType?, transmission?, bodyStyle? } } } } }

/users
  {uid}:
     profile: { displayName, email, createdAt, vendorId }
     auth:    { algo: "PBKDF2WithHmacSHA256", iterations, salt(base64), hash(base64) }
/usersByEmail
  {emailKey}: uid                     # emailKey = email lowercased, '.'→',' for RTDB key safety
```

Notes:
- Sorting via explicit `order` fields keeps RTDB (unordered map) rendering deterministic and hashable.
- `options` lets the questionnaire define arbitrary choice answers per question (beyond the fixed enums),
  satisfying "configurable questionnaire and options."
- The vehicle catalog replaces/augments `VinDecoder`'s hardcoded map for manual selection.

---

## 7. Baseline questionnaire (assets) + first-run seeding

- Add `app/src/main/assets/baseline_questionnaire.json` — a serialized `QuestionnaireConfig`
  produced from today's `ChecklistCatalog` (so no functional regression) plus `allowImage`/`maxImages`
  derived from the current `photoCapable` + global max (10).
- Add `app/src/main/assets/baseline_vehicle_catalog.json` — a starter make/model/variant set.
- **Seeding logic** (`BaselineCatalogSeeder`), on first successful login when the vendor RTDB has no
  `/config/questionnaire`:
  1. Read baseline JSON from assets.
  2. Compute version = 1 and canonical hash.
  3. Write to RTDB `/config/questionnaire` (and `/vehicleCatalog`).
  4. Adopt locally as the active questionnaire.
- If RTDB already has config, seeding is skipped and the RTDB version is fetched.
- The baseline JSON is also the **offline fallback** if the very first login cannot reach RTDB — the app
  operates on the baseline until a real sync happens.

---

## 8. Custom auth (account creation + login)

### Model
```kotlin
// :core:model/auth
data class AppUser(val uid: String, val displayName: String, val email: String, val vendorId: String, val createdAt: Long)
```

### Sign-up (requires connectivity)
1. Validate email/password (strength + format).
2. Derive `salt` (secure random) + `hash = PBKDF2(password, salt, iterations)` **on device**.
3. Generate `uid` (UUID).
4. Transaction: fail if `/usersByEmail/{emailKey}` exists; else write `/users/{uid}` + `/usersByEmail`.
5. Persist a local encrypted credential cache (email, uid, salt, hash, displayName) for offline re-login.
6. Issue a `Session` (reuse existing `Session` + `SessionStore`).

### Login
- **Online**: read `/usersByEmail/{emailKey}` → `/users/{uid}/auth`, recompute PBKDF2 with stored salt,
  constant-time compare. On success cache credential + issue session.
- **Offline**: if no connectivity, verify against the local encrypted credential cache (same PBKDF2
  compare). Only possible for a previously-logged-in account on this device.

### Files touched
- `AuthRepositoryImpl` (rewrite to use `RemoteUserSource` + credential cache; drop `FirebaseAuth`).
- `AuthRepository` port gains `suspend fun signUp(displayName, email, password): AppResult<Session>`.
- New `LoginScreen` "Create account" entry + `SignupScreen` + `SignupViewModel`.

### Security note (ties to O-2)
Custom credentials in RTDB is inherently weaker than Firebase Auth: RTDB rules cannot verify a
password, and login must read the user node before any Firebase auth exists (bootstrapping problem).
Mitigations baked into this plan:
- Store only a **salted PBKDF2 hash**, never plaintext; TLS in transit (RTDB default).
- Recommend (O-2) enabling **Firebase Anonymous Auth** so `/users` reads/writes require `auth != null`
  in rules, preventing a fully world-readable credential store.
- Rate-limit login attempts locally; never log credentials.
This risk is documented and accepted per decision D-2; revisiting Firebase Auth remains the safer option.

---

## 9. Configurable questionnaire consumption

### New serializable config models (`:core:model/catalog/QuestionnaireConfig.kt`)
Mirror the existing `ChecklistSection/Group/Item` but as `@Serializable` data classes with the added
`allowImage`, `maxImages`, `order`, and `options`. The static `ChecklistCatalog` object is retained only
to **generate the baseline JSON**; runtime code reads the active `QuestionnaireConfig` instead.

### Active vs snapshot
- **Active questionnaire**: the version the app currently uses for *new* inspections. Cached in Room
  (`config_cache`) + pointer in `ConfigStore`.
- **Snapshot**: at `startInspection`, copy the active questionnaire JSON + version + hash into the
  inspection row. All checklist rendering, capture limits, and reporting for that inspection read from
  its snapshot — never the active/live config.

### Per-question image rules
- Capture screens read `allowImage`/`maxImages` from the **inspection's snapshot item**, replacing
  `BuildConfig.MAX_IMAGES_PER_ITEM`. Items with `allowImage=false` show no photo grid.

### Files touched
`ChecklistCatalog` consumers: `ChecklistUseCases`, `ChecklistHubViewModel`, `ChecklistSectionViewModel`,
`SectionCaptureViewModel`, `ImageDetailViewModel`, `ReportBuilder`, `PdfReportGenerator`,
`CompletenessCalculator`, `ImageLabels`, `CaptureOrder` — switch from `ChecklistCatalog.sections` to a
provided `QuestionnaireConfig` (from the inspection snapshot, or the active config for new inspections).

---

## 10. Sync gating (adopt new config)

`SyncConfigUseCase` fetches the latest RTDB questionnaire/vehicle-catalog version and decides whether to
**adopt** it as active:

```text
canAdopt = (localInspectionCount == 0) OR (event == RE_LOGIN_AFTER_LOGOUT)
```

- On **login**: fetch latest config. If `canAdopt`, set it active (and cache). Otherwise keep the current
  active version and record "update available" (informational only).
- Mid-session config changes are **never** auto-applied.
- Because inspections are snapshotted, adopting a new active version is always safe for existing data.

`ConfigStore` records: `activeVersion`, `activeHash`, `availableVersion`, `lastSyncAt`.

---

## 11. Version & compatibility (hashing)

- Canonical JSON = keys sorted, whitespace-normalized; `hash = SHA-256(canonicalJson)`.
- `hash` is embedded in: RTDB config, each inspection snapshot, and the export manifest.
- Compatibility = exact `hash` equality (D-4). Version int is for human/audit readability; the hash is
  authoritative.

---

## 12. Export feature (zip + share)

### Structure
```text
VSP_Export_{displayName}_{yyyyMMdd_HHmmss}/        (root)
  manifest.json
  questionnaires/
    {questionnaireHash}.json         # every distinct snapshot referenced by exported inspections
  inspections/
    {inspectionId}/
      data.csv                       # long-format (see §12.2)
      images/
        {imageId}.jpg …              # decrypted copies of the stored photos
  -> zipped to VSP_Export_….zip -> shared via FileProvider + Intent.ACTION_SEND
```

### 12.1 manifest.json
```json
{
  "appVersion": "0.2.0",
  "vendorId": "acme",
  "exportedAt": 1751600000000,
  "inspector": { "uid": "…", "email": "…", "displayName": "…" },
  "questionnaire": { "version": 7, "hash": "sha256:ab12…" },
  "inspectionCount": 12,
  "inspections": [ { "id": "…", "questionnaireHash": "sha256:ab12…", "images": 34 } ]
}
```

### 12.2 data.csv (long format, D-6)
Header:
`record_type,inspection_id,section_id,section_title,group_id,group_title,item_id,item_label,response_type,status,rating,numeric_value,unit,text_value,damage_types,image_files,updated_at`
- `record_type = META` rows carry vehicle + inspection metadata (VIN, category, make/model/variant,
  odometer, scores, recommendation, timestamps) using `item_id` as the meta key.
- `record_type = ITEM` rows carry one checklist response each; `image_files` = `;`-separated image
  filenames present under `images/`.
- `record_type = ANNOTATION` rows carry manual damage-assessment findings tied to an image.
- CSV is RFC-4180 quoted; UTF-8 with BOM for spreadsheet friendliness.

### Pipeline
`ExportDataUseCase` → gather inspections (Room) → decrypt images to a temp dir → write CSV + manifest +
questionnaire snapshots → zip → hand path to share sheet. Runs off the main thread (WorkManager or a
coroutine on IO); shows progress. Temp files cleaned after share.

---

## 13. Import feature (device migration, validated)

`ImportDataUseCase` (preceded by `ValidateImportUseCase`):

1. **Structural validation**: zip opens; `manifest.json` present and parseable; each listed inspection
   has a folder with `data.csv`; referenced `image_files` exist; `questionnaires/{hash}.json` present for
   every referenced hash.
2. **Questionnaire compatibility (hard block, D-4)**: for every inspection, its
   `questionnaireHash` must equal the app's **current active questionnaire hash**. Any mismatch →
   abort with `ImportError.QuestionnaireMismatch(expectedHash, foundHash)` and a clear user message.
   (Also blocks when the embedded questionnaire definition's recomputed hash ≠ the manifest hash =
   tampered/corrupt bundle.)
3. **CSV↔questionnaire match**: every `item_id` in `ITEM` rows must exist in the active questionnaire,
   and every mandatory item must be represented. Column header must match the expected schema. Mismatch → block.
4. **Apply** (only if all pass): insert Vehicle/Inspection/ChecklistResponse/Annotation rows into Room,
   copy images into the encrypted file store, write the inspection snapshot from the embedded
   questionnaire JSON. Conflict policy per O-3 (default: skip inspection ids already present).
5. Wrap in a DB transaction per inspection; partial failure rolls back that inspection and reports it.

This satisfies: "import allowed only when same-structured zip"; "questionnaire + CSV must match";
"admin-changed questionnaire not matching imported zip → don't allow"; "change device without losing data."

---

## 14. Removal of cloud inspection sync

Because captured data no longer goes to the cloud (D-1), the existing Firestore/Storage upload path is
retired:
- Remove/disable `SyncWorker` image + report uploads and `FirebaseRemoteDataSource`.
- `SyncRepository`/`observeSyncStatus` is repurposed to reflect **local** state only (or removed from UI).
- `firebase-firestore-ktx` and `firebase-storage-ktx` deps can be dropped; `firebase.rules` (Firestore)
  is replaced by RTDB rules (§16).
- Keep Crashlytics/Analytics optional.

If any residual "sync" UI remains, it now means "sync configuration" (§10), not data upload.

---

## 15. Local persistence changes (Room v4 → v5)

Additive, non-destructive migration.

- `inspections`: add `checklist_version INTEGER`, `checklist_hash TEXT`, `checklist_snapshot_json TEXT`.
- New table `config_cache(id, type, version, hash, json, fetchedAt, isActive)` — cached active/available
  questionnaire + vehicle catalog.
- New table `app_users(uid, email, displayName, algo, iterations, salt, hash, cachedAt)` — encrypted-at-rest
  credential cache for offline re-login.
- `inspection_images`: no change (per-item `maxImages` is enforced at capture time from the snapshot).
- Migration `MIGRATION_4_5` backfills existing inspections with the current baseline snapshot + hash so
  legacy inspections remain renderable and exportable.

---

## 16. RTDB security rules (replace Firestore rules)

`firebase/database.rules.json` (illustrative; assumes O-2 anonymous-auth gating):
```json
{
  "rules": {
    "config": {
      ".read": "auth != null",
      ".write": false               // admins write via console / privileged tooling only
    },
    "users": {
      "$uid": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "usersByEmail": {
      ".read": "auth != null",
      ".write": "auth != null"
    }
  }
}
```
Notes: `config` is read-only to clients (edits happen out-of-band by the vendor admin); seeding of a
brand-new vendor DB is done with an elevated token or a one-time console import of the baseline JSON.

---

## 17. Build config & multi-vendor (local.properties)

Extend the existing `local.properties` → `BuildConfig` pattern in `app/build.gradle.kts`. Concrete
values for the current ReadyWire vendor build:
```
FIREBASE_DB_URL=https://readywire-vehicle-inspection-f9a02.firebaseio.com/
FIREBASE_PROJECT_ID=readywire-vehicle-inspection-f9a02
FIREBASE_APP_ID=<app-id>
FIREBASE_API_KEY=<web-api-key>
VENDOR_ID=readywire
```
> The DB URL above is a legacy `firebaseio.com` domain; the SDK accepts it directly. `FIREBASE_APP_ID`
> and `FIREBASE_API_KEY` come from the Firebase console (Project settings → your Android/web app) and
> are required by `FirebaseOptions` even when only RTDB is used. `local.properties` is git-ignored.
- `buildConfigField`s: `FIREBASE_DB_URL`, `FIREBASE_PROJECT_ID`, `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `VENDOR_ID`.
- `MAX_IMAGES_PER_ITEM` build field is **deprecated** in favor of per-question `maxImages` (kept as a
  fallback default only).
- `FirebaseAppInitializer` builds `FirebaseOptions` from these fields at startup, so **no
  `google-services.json` is required** and each vendor build targets its own RTDB. Missing URL → app runs
  on baseline assets offline (graceful, mirrors current Gemini-key handling).

---

## 18. Dependencies

- **Add**: `com.google.firebase:firebase-database-ktx` (via existing Firebase BOM).
- **Add**: (if O-2) `firebase-auth-ktx` retained only for **anonymous** sign-in gating.
- **Remove/optional**: `firebase-firestore-ktx`, `firebase-storage-ktx`.
- Zip/CSV use JDK (`java.util.zip`, manual CSV writer) — no new libs.
- PBKDF2 via `javax.crypto` (JDK) — no new libs. Credential cache encryption reuses
  `androidx-security-crypto` (already in catalog).

---

## 19. Testing strategy

- **Unit**: PBKDF2 hash/verify; canonical hashing determinism; snapshot pinning (config change does not
  alter existing inspection); sync-gating logic (adopt only when empty or re-login); CSV writer/reader
  round-trip; import validators (structure, hash mismatch → block, CSV column mismatch → block).
- **Migration**: Room v4→v5 with legacy inspection backfill.
- **Integration**: export → import round-trip on a second (fresh) install reproduces inspections
  byte-faithfully; mismatch bundle is rejected; RTDB seed-on-first-run against the Firebase emulator.
- **UI**: signup/login flows; settings export/import actions; per-question image cap enforcement.
- Gates unchanged from `001` (tests required for new use cases/screens).

---

## 20. Phased delivery

1. **P1 — Config plumbing**: `QuestionnaireConfig` models, baseline JSON asset, `FirebaseAppInitializer`,
   RTDB config source, `config_cache`, migrate consumers off static `ChecklistCatalog` (active config).
2. **P2 — Snapshot + gating**: inspection snapshot columns + `MIGRATION_4_5`; `SyncConfigUseCase` gating;
   per-question `allowImage`/`maxImages` enforcement.
3. **P3 — Custom auth**: RTDB user source, signup/login rewrite, credential cache, offline re-login.
4. **P4 — Vehicle catalog**: RTDB make/model/variant + identify-screen consumption.
5. **P5 — Export**: CSV + zip + share.
6. **P6 — Import**: validators (hard-block) + apply + device-migration.
7. **P7 — Cleanup**: retire Firestore/Storage sync; RTDB rules; docs.

Each phase is independently shippable and testable.

---

## 21. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Custom RTDB auth is weaker than Firebase Auth (bootstrapping, no server-side password check) | Salted PBKDF2, anonymous-auth gating (O-2), local rate limiting; documented, revisit Firebase Auth. |
| RTDB map ordering is not guaranteed | Explicit `order` fields + canonical sorting before hashing/rendering. |
| Large exports (many images) block UI / exceed storage | Run in WorkManager; stream zip; temp-dir cleanup; optional per-inspection export. |
| Legacy (v4) inspections lack a snapshot | `MIGRATION_4_5` backfills baseline snapshot + hash. |
| Hash mismatch from trivial formatting differences | Single canonicalization routine shared by writer, seeder, and validator. |
| Vendor ships without RTDB reachable on first run | Baseline assets provide a working offline questionnaire until first successful sync. |

---

## 22. Requirement traceability

| Requirement (user) | Addressed in |
|--------------------|--------------|
| Use RTDB instead of Firestore | §5, §6, §14, §18 |
| User model + account creation + login | §6 (/users), §8 |
| Make/model/variant static data on server | §6 (/vehicleCatalog), §7, P4 |
| Checklist questions/options in Firebase | §6 (/config/questionnaire), §9 |
| Fetch categories/questions/options/config on login | §10 |
| Config changes don't affect users who already captured | §3 D-3, §9 snapshot, §11 |
| Sync only on logout/relogin or no inspection data | §10 |
| Export all data as shareable zip (root → per-inspection → images + CSV) | §12 |
| Import same-structured zip; questionnaire + CSV must match | §13 |
| Admin-changed questionnaire not matching zip → block import | §13 step 2–3 (hard block) |
| Firebase URL per vendor in local.properties | §17 |
| Per-question configurable: allow image, number of images | §6 (allowImage/maxImages), §9 |
| Baseline JSON in assets; admin can override in Firebase; auto-import for new vendors | §7 |
