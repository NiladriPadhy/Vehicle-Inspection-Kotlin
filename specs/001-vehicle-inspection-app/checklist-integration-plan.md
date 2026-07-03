# Vehicle Inspection Checklist — Integration Plan

**Status:** Implemented + evolved to **checklist-first** (all 8 phases delivered, plus the
post-integration enhancements in §11; builds + installs verified)
**Owner:** Engineering
**Last updated:** 2026-07-03
**Related:** `spec.md` (FR-049…FR-062), `plan.md`, `data-model.md`, `contracts/report-json-schema.json`

> **Evolution note:** The original plan (§1–§10) wrapped the checklist *around* the existing
> linear capture wizard. The delivered app has since been restructured to be **checklist-first**:
> the checklist is the primary interface after identification, and photo capture + damage marking
> happen from within each checklist item. See **§11 Post-integration enhancements** for the
> current behavior, which supersedes parts of §6 (UI/Navigation) and §7 (Report) below.

---

## 1. Objective

Integrate a comprehensive, 15-section vehicle inspection checklist into the app for **both New and
Old vehicles**, while **preserving the existing dent/scratch photo-marking** (capture wizard, Gemini
AI detection, green AI bounding boxes, blue manual pins, swipe navigation, Image-detail flow).

The checklist wraps around the existing photo flow; it does not replace it.

## 2. Guiding principles

- **Catalog-driven, not hard-coded.** One generic checklist engine renders all sections from a data
  catalog. Adding/removing items later means editing the catalog only.
- **`appliesTo` filtering** drives New vs Old differences — no duplicated screens.
- **Offline-first + syncable**, consistent with the current Room + WorkManager architecture.
- **Preserve existing AI/annotation logic** for damage marking on photos.
- **Non-gating.** The checklist is fillable progressively and does not block Final Verification.

## 3. Key decisions (confirmed)

| Decision | Choice |
|----------|--------|
| New-vehicle scope | **Cosmetic / PDI focus.** Hidden for New: §2 Documents, §11 Mechanical, §12 Road Test; detailed §5 Underbody / §6 Engine Bay remain Old-focused. All items remain manually markable **N/A**. |
| Placement in flow | **Accessible anytime** from Dashboard and Review (progressive fill). Does **not** gate Final Verification. |
| Repair cost (§13) | **Manual entry only** by the inspector (no AI cost estimate). |
| Photo damage marking | **Unchanged** — existing capture + AI + annotation flow is reused. |

## 4. New vs Old applicability

| Section | New | Old |
|---|---|---|
| 1 Vehicle Information | ✅ (odometer optional, reg optional) | ✅ full (odometer, registration) |
| 2 Documents Verification | ➖ hidden | ✅ full |
| 3 Exterior Inspection | ✅ | ✅ |
| 4 Wheels & Tyres | ✅ (cosmetic: rim/alloy scratches, nuts) | ✅ (tread, pressure, sidewall) |
| 5 Underbody | ➖ basic/hidden | ✅ full (leaks, rust, chassis) |
| 6 Engine Bay | ➖ basic/hidden | ✅ full (fluids, wear, noise) |
| 7 Interior Inspection | ✅ | ✅ |
| 8 Electrical | ✅ | ✅ |
| 9 Air Conditioning | ✅ | ✅ |
| 10 Safety Features | ✅ | ✅ |
| 11 Mechanical | ➖ hidden | ✅ |
| 12 Road Test | ➖ hidden | ✅ |
| 13 Damage Assessment | ✅ | ✅ |
| 14 Photo Checklist | ✅ (existing captures) | ✅ |
| 15 Final Assessment | ✅ | ✅ |

Legend: ✅ shown, ➖ hidden by default (still overridable to N/A where relevant).

## 5. Data model

### 5.1 Catalog (`core:model`)

A static `ChecklistCatalog` defining the hierarchy:

```
ChecklistSection(id, title, order, appliesTo)
 └─ ChecklistGroup(id, title, order)          // e.g. "Front", "Left Side", "Front Left Wheel"
     └─ ChecklistItem(id, label, responseType, appliesTo, unit?, mandatory)
```

`Applicability` enum: `NEW`, `OLD`, `BOTH`.

`ResponseType` enum:
- `STATUS_OK` — OK / Not OK / N/A
- `YES_NO` — Yes / No (+ remark)
- `PASS_FAIL` — Pass / Fail (+ remark)
- `RATING_1_5` — 1..5 (Final Assessment)
- `TEXT` — free text (remarks)
- `NUMBER` — numeric with unit (tread depth mm, pressure psi, odometer km, cost)
- `COMPONENT` — present toggle + damage-type multi-select; links to captured photos & marks

### 5.2 Persistence (`core:data`, Room DB **v2** + migration)

```
ChecklistResponseEntity(
  id: String (PK),
  inspectionId: String (indexed),
  itemId: String,            // references ChecklistCatalog item id
  status: String?,           // OK/NOT_OK/NA/YES/NO/PASS/FAIL
  rating: Int?,              // 1..5 for RATING_1_5
  numericValue: Double?,     // tread/pressure/odometer/cost
  textValue: String?,        // remarks / free text
  damageTypesCsv: String?,   // for COMPONENT items
  updatedAt: Long,
  syncState: String
)
```

- New DAO: `ChecklistResponseDao` (observe by inspection, upsert, get pending uploads).
- Add entity to `VspDatabase`, bump `version = 2`, add `Migration(1,2)` creating the table.
- Register in `RepositoryModule` + DI.

### 5.3 Domain (`core:domain`)

- `ChecklistResponse` model + `ChecklistRepository` interface.
- Use cases: `ObserveChecklistUseCase`, `SaveChecklistItemUseCase`, `GetChecklistCompletenessUseCase`.

### 5.4 Damage Assessment (§13) — extend existing records

Add optional fields to the existing finding/annotation domain + entities (no parallel system):
`component`, `vehicleSide`, `estimatedSize`, `repairRequired`, `estimatedCost` (manual),
`manualVerified`. AI continues to populate `damageType`, `confidence`, `severity`, `boundingBox`
exactly as today.

### 5.5 Final Assessment (§15)

- Category ratings (Exterior, Interior, Engine, Electrical, Tyres, Suspension, Safety,
  Documentation) stored as `RATING_1_5` checklist items.
- Reuse `Inspection.overallCondition`; **expand** `finalRecommendation` to the 6 options:
  `NO_REPAIR`, `COSMETIC_REPAIR`, `MECHANICAL_SERVICE`, `BODY_SHOP_REPAIR`, `INSURANCE_CLAIM`,
  `REJECT_VEHICLE`.

## 6. UI / Navigation

- **Checklist Hub** screen: lists applicable sections with per-section completion badges
  (e.g., "Engine Bay 8/12"), filtered by New/Old.
- **Checklist Section** screen: renders items dynamically by `responseType` (toggle chips, rating,
  number+unit field, remark text field).
- **Component items** deep-link to the existing capture / Image-detail so damage marks are captured
  there — reusing current logic.
- **Entry points:** from **Dashboard** (per inspection) and from **Review**. Non-gating.
- New routes:
  - `ChecklistHub(inspectionId)`
  - `ChecklistSection(inspectionId, sectionId)`

Resulting flow (checklist optional/parallel):
`Login → Dashboard → Start → Identify → [Old Docs] → Exterior → Interior → Review → Final
Verification → Report`, with **Checklist Hub** reachable from Dashboard/Review at any time.

## 7. Report + PDF integration

- Extend `ReportBuilder` JSON with:
  - `checklist` block (sections → items → responses),
  - `damageAssessment` array (per damage: component, side, type, severity, size, confidence,
    repair required, cost, manual verified),
  - `finalAssessment` (category ratings, overall condition, recommendation).
- Extend `PdfReportGenerator` (already scaffolded) with new pages:
  - Documents table (Old),
  - per-section checklist tables (OK/Not OK/NA, Yes/No, Pass/Fail),
  - Wheels & Tyres, Road Test pass/fail,
  - **Damage Assessment table**,
  - Final Assessment ratings.
- **Keep** the existing per-photo highlighted damage pages as-is.

## 8. Phased delivery

1. **Catalog + data layer** — `ChecklistCatalog`, entity, DAO, DB v2 migration, repository, use
   cases, DI.
2. **Checklist Hub + Section UI** — dynamic rendering, save/observe, completeness, New/Old
   filtering, nav wiring from Dashboard & Review.
3. **Vehicle Info + Documents** — bind §1 to existing Vehicle fields (add **odometer**); §2 Yes/No +
   remarks (Old only).
4. **Component sections (§3, §7) ↔ photo marking** — link components to capture/Image-detail; keep
   dent/scratch marking intact.
5. **Status/Pass-Fail sections** — §4 Wheels, §5 Underbody, §6 Engine Bay, §8 Electrical, §9 AC,
   §10 Safety, §11 Mechanical, §12 Road Test.
6. **Damage Assessment (§13)** — extra fields on findings/annotations + editor UI (manual cost).
7. **Final Assessment (§15)** — ratings + expanded recommendation enum.
8. **Report + PDF** — JSON schema + PDF sections; build/install verification.

## 9. Risks & notes

- **DB migration**: shipping DB v2 requires a tested `Migration(1,2)`; fallback destructive
  migration only in debug.
- **Catalog size**: the full checklist is large; catalog-driven rendering keeps UI code small but
  the catalog file will be sizeable (acceptable, data-only).
- **Pending item**: the PDF export feature (`PdfReportGenerator`, FileProvider, Export button) from
  the prior work was edited but its build was interrupted before verification — this will be
  confirmed compiling as part of Phase 8 (or earlier if PDF work resumes first).
- **Photo Checklist (§14)** largely maps to existing capture positions; it will be represented as a
  coverage view rather than duplicate capture UI.

## 10. Out of scope (this iteration)

- AI-based repair cost estimation (manual only per decision).
- Automated road-test telemetry.
- Localization of checklist labels (English first; catalog structure allows adding later).

---

## 11. Post-integration enhancements (checklist-first, delivered 2026-07-03)

These supersede the linear-wizard assumptions in §6/§7. All items build + install verified.

### 11.1 Checklist-first flow
- After identification the app loads the **checklist directly** (both New and Old). The separate
  linear exterior/interior capture wizard is no longer the primary path.
- **Old vehicles skip** the dedicated document-capture screen; RC/PUC/Insurance are captured as
  photo-capable items inside the checklist's Documents section.
- Finalize gating relaxed: requires **≥ 1 captured photo** rather than a fixed wizard set.

### 11.2 Per-item photos
- Photos are tagged with `checklistSectionId` + `checklistItemId` (`InspectionImage`, DB v3/v4)
  so **each checklist item keeps its own distinct image set**.
- Each photo-capable item renders an **image grid** (max `MAX_IMAGES_PER_ITEM`, configurable via
  `app` `BuildConfig`, default 10) with an add-photo tile and per-image **delete** (confirm →
  removes DB row + file).
- `photoCapable` flag on `ChecklistItem` intelligently enables capture only where visual evidence
  is meaningful (panels, wear, leaks, tyres, documents), not for purely functional checks.

### 11.3 Continuous capture screen
- The Add-photo screen captures **continuously until back**, showing **Total / Remaining / This
  session**; on back it prompts **Keep** or **Discard** (discard deletes the session's photos).

### 11.4 Condition grades
- COMPONENT items use granular grades: **Good, Minor scratches/wear, Major scratches/wear,
  Damage, N/A** (`ChecklistStatus` extended), with wording adapting per area (interior "wear" vs
  exterior "scratches").

### 11.5 Image labels & detail gallery
- Labels show the full path **Section → Group → Item** (e.g. "Exterior Inspection → Front → Front
  Bumper"). The Image Detail gallery/swipe is **scoped to the item** (fallback section →
  inspection), in capture-sequence order.

### 11.6 Deletion
- **Delete image** (per-item grid) and **delete inspection** (dashboard, cascades all rows +
  files) with confirmations. New `deleteImage` / `deleteInspection` repository methods + use cases.

### 11.7 Report (JSON + PDF) for the new workflow
- **JSON**: photos are **nested under their checklist item** (with a human-readable
  `checklistItem` label); only untagged/legacy captures remain in the top-level `images[]`.
  `damageAssessment` rows carry `checklistItemId`/`checklistItem`. Ordering follows catalog order.
- **PDF**: redesigned into a **branded, multi-section report** — cover, contents, at-a-glance,
  category-ratings summary (X/5 + condition sentence), per-category **parameter tables** with
  Perfect/Imperfect counts, a photo **gallery**, and **damage-evidence** pages (AI green boxes,
  manual blue pins). Category/overall ratings derive from Final Assessment ratings or the
  perfect-ratio of answered items.
- **PDF size controls** (`core:data` `BuildConfig`, overridable via `local.properties`/env):
  `PDF_IMAGE_QUALITY` (70), `PDF_GALLERY_IMAGE_WIDTH` (640), `PDF_DAMAGE_IMAGE_WIDTH` (1280),
  `PDF_MAX_IMAGES` (120). Each photo is embedded **at most once** (marked → damage evidence,
  rest → gallery) with tight downscale + JPEG re-encode.

### 11.8 Misc
- Login screen hard-codes `test`/`test` for testing (offline auth fallback).
- Report is generated on demand and cached; re-tapping **Generate** rebuilds from current data.
