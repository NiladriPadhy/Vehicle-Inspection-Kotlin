# Data Model: Android Vehicle Inspection Application

**Feature**: `001-vehicle-inspection-app` | **Date**: 2026-07-01

Covers domain entities, the local Room schema (source of truth), the Firestore schema, and
the Firebase Storage hierarchy. Enums are shared domain types in `:core:model`.

## Enums (domain)

```kotlin
enum class InspectionContext { PRE_DELIVERY, RENTAL, AUCTION, INSURANCE_CLAIM, SERVICE, RESALE, HANDOVER }
enum class InspectionStatus { DRAFT, IN_PROGRESS, COMPLETED, SYNCED }
enum class VehicleCategory { NEW, OLD }
enum class Section { EXTERIOR, INTERIOR, DOCUMENT }
enum class DocumentType { RC, POLLUTION_CERTIFICATE, INSURANCE }  // Old-vehicle documents
enum class ImageQuality { OK, BLURRY, DARK, OVEREXPOSED, INCOMPLETE }
enum class CaptureState { PENDING, CAPTURED, SKIPPED }
enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }
enum class AnnotationShape { RECTANGLE, FREEHAND, PIN }
enum class FindingSource { INITIAL, REVERIFICATION, FINAL }
enum class SyncState { PENDING, UPLOADING, SYNCED, FAILED }
enum class VinInputMethod { MANUAL, OCR, BARCODE }
// DamageType is a large closed enum covering the exterior + interior taxonomy from the spec
enum class DamageType { DENT, SCRATCH, DEEP_SCRATCH, PAINT_CHIP, /* ... full taxonomy ... */ OTHER }

// Final-recommendation options (6) selected on the Final Assessment checklist item.
enum class RepairRecommendation {
    NO_REPAIR, COSMETIC_REPAIR, MECHANICAL_SERVICE, BODY_SHOP_REPAIR, INSURANCE_CLAIM, REJECT_VEHICLE
}
```

### Checklist catalog enums (`:core:model/catalog`)

```kotlin
enum class Applicability { NEW, OLD, BOTH }
enum class ChecklistResponseType { STATUS_OK, YES_NO, PASS_FAIL, RATING_1_5, TEXT, NUMBER, COMPONENT }
// Includes component condition grades appended for the checklist-first workflow.
enum class ChecklistStatus {
    OK, NOT_OK, NA, YES, NO, PASS, FAIL,
    GOOD, MINOR_SCRATCHES, MAJOR_SCRATCHES, DAMAGE, // COMPONENT condition grades
}
```

The catalog is a static hierarchy `ChecklistSection(id, title, order, appliesTo) → ChecklistGroup
(id, title) → ChecklistItem(id, label, responseType, appliesTo, unit?, mandatory, photoCapable)`.
`photoCapable` gates whether an item exposes a per-item image grid (body panels, wear, leaks,
tyres, documents = true; purely functional checks = false).

## Entity relationships

```text
Inspector 1───* Inspection *───1 Vehicle
Inspection 1───* InspectionImage 1───* AIFinding
                              └────* Annotation
Inspection 1───1 Report
Inspection 1───* AuditLogEntry
(SyncTask references any syncable entity by type + id)
```

---

## Domain entities

### Inspector
| Field | Type | Notes |
|-------|------|-------|
| id | String | auth uid |
| displayName | String | |
| email | String | |

### Vehicle
| Field | Type | Notes |
|-------|------|-------|
| id | String | local uuid |
| vin | String? | 17-char when present |
| category | VehicleCategory | NEW or OLD |
| year | Int? | |
| manufacturer | String? | |
| make | String? | |
| model | String? | |
| variant | String? | |
| trim | String? | |
| bodyStyle | String? | |
| fuelType | String? | |
| transmission | String? | |
| color | String? | |
| registrationNumber | String? | required when `category == OLD` |
| engineNumber | String? | optional |
| chassisNumber | String? | optional |
| numberOfOwnerships | Int? | required when `category == OLD` |
| numberOfKeys | Int? | required when `category == OLD` |
| vinInputMethod | VinInputMethod | how VIN was captured |
| decoded | Boolean | true if from VIN lookup |

**Validation**: if `vin != null` → length 17, valid characters, check-digit validated. On
lookup failure, `manufacturer/model/variant/year/color` required via manual entry. When
`category == OLD`: `registrationNumber`, `numberOfOwnerships` (≥ 0), and `numberOfKeys`
(≥ 0) are required, and RC / Pollution Certificate / Insurance document images
(`Section.DOCUMENT`) must be present (or skipped-with-reason). When `category == NEW`,
these Old-only fields/documents are not required.

### Inspection
| Field | Type | Notes |
|-------|------|-------|
| id | String | local uuid (also Firestore doc id) |
| inspectorId | String | FK → Inspector |
| vehicleId | String | FK → Vehicle |
| context | InspectionContext | |
| vehicleCategory | VehicleCategory | NEW/OLD (drives required steps) |
| status | InspectionStatus | DRAFT→IN_PROGRESS→COMPLETED→SYNCED |
| currentStep | String | wizard resume pointer |
| createdAt | Long | epoch millis |
| updatedAt | Long | |
| completedAt | Long? | |
| gpsLat / gpsLng | Double? | optional |
| deviceInfo | String | model/OS |
| exteriorScore / interiorScore / safetyScore / cosmeticScore / confidenceScore | Int? | 0–100 |
| overallCondition | String? | derived |
| finalRecommendation | String? | |
| summary | String? | AI summary |
| syncState | SyncState | |

**State transitions**: `DRAFT → IN_PROGRESS` (first capture) → `COMPLETED` (finalized,
passes completeness gate) → `SYNCED` (fully uploaded). Only `DRAFT/IN_PROGRESS` are
resumable/editable.

### InspectionImage
| Field | Type | Notes |
|-------|------|-------|
| id | String | local uuid |
| inspectionId | String | FK |
| section | Section | EXTERIOR / INTERIOR / DOCUMENT |
| position | String | wizard captures: `FRONT`, `DASHBOARD`; checklist captures: `<checklistItemId>_<uuid>` (unique per photo) |
| documentType | DocumentType? | set for legacy DOCUMENT wizard captures; null for checklist-tagged document photos |
| checklistSectionId | String? | catalog section id this photo documents (checklist-first flow) |
| checklistItemId | String? | catalog item id this photo documents; drives per-item grids, labels, and report nesting |
| captureState | CaptureState | PENDING/CAPTURED/SKIPPED |
| skipReason | String? | required when SKIPPED |
| localFilePath | String | encrypted file path |
| thumbnailPath | String? | |
| remoteUrl | String? | Storage URL after upload |
| width / height | Int? | |
| sizeBytes | Long? | |
| capturedAt | Long? | |
| orientation | Int? | |
| quality | ImageQuality | must be OK to accept |
| aiState | SyncState | analysis queue state |
| syncState | SyncState | upload state |

**Validation**: In the checklist-first flow, photos are attached to individual checklist
items (via `checklistItemId`) and finalize requires at least one captured photo rather than a
fixed set of wizard positions. Each photo-capable item keeps its own set of photos, bounded
by `MAX_IMAGES_PER_ITEM`. Damage-detection AI runs on `EXTERIOR`/`INTERIOR` images; document
photos are stored/reported but not analyzed. Individual images may be deleted (DB row + file);
deleting an inspection cascades to all its images/files.

### AIFinding
| Field | Type | Notes |
|-------|------|-------|
| id | String | local uuid |
| imageId | String | FK |
| damageType | DamageType | validated enum |
| confidence | Float | 0.0–1.0 |
| severity | Severity | |
| bboxX / bboxY / bboxW / bboxH | Float | normalized 0–1 |
| repairRecommendation | String | |
| reviewRequired | Boolean | true if confidence < threshold or flagged |
| source | FindingSource | INITIAL/REVERIFICATION/FINAL |
| createdAt | Long | |

**Validation (mandatory before persist)**: required fields present; `damageType` maps to a
known enum; `confidence ∈ [0,1]`; `severity` valid; bbox within `[0,1]` and `w,h > 0`.
Invalid → rejected (not stored), `AiInvalidResponse`, retryable.

### Annotation
| Field | Type | Notes |
|-------|------|-------|
| id | String | local uuid |
| imageId | String | FK |
| shape | AnnotationShape | RECTANGLE/FREEHAND/PIN |
| geometryJson | String | normalized points/rect/pin coords |
| damageType | DamageType | |
| severity | Severity | LOW/MEDIUM/HIGH/CRITICAL |
| comment | String? | |
| component | String? | damage-assessment: affected component (manual) |
| vehicleSide | String? | damage-assessment: side/location (manual) |
| estimatedSize | String? | damage-assessment: size descriptor (manual) |
| repairRequired | Boolean? | damage-assessment: whether repair is required (manual) |
| estimatedCost | Double? | damage-assessment: manual repair cost estimate (no AI cost) |
| manualVerified | Boolean | damage-assessment: inspector-verified flag |
| createdAt / updatedAt | Long | |

**Validation**: geometry non-empty for the shape; severity required. Unlimited per image.
Damage-assessment fields are optional and manually entered.

### ChecklistResponse
| Field | Type | Notes |
|-------|------|-------|
| id | String | `<inspectionId>_<itemId>` |
| inspectionId | String | FK → Inspection |
| itemId | String | references a `ChecklistCatalog` item id |
| status | ChecklistStatus? | OK/NOT_OK/NA/YES/NO/PASS/FAIL or condition grade (GOOD/MINOR_SCRATCHES/…) |
| rating | Int? | 1..5 for RATING_1_5 (Final Assessment) |
| numericValue | Double? | tread/pressure/odometer/etc. |
| unit | String? | unit for numeric values |
| textValue | String? | remarks; for `fa_recommendation` holds a `RepairRecommendation` name |
| damageTypes | List<DamageType> | for COMPONENT items (CSV in DB) |
| updatedAt | Long | |

**Validation**: `isAnswered` when any of status/rating/numericValue/textValue/damageTypes is
set. Persisted per item; drives checklist completion, the report checklist block, category
ratings, and derived section/overall ratings in the PDF.

### Report
| Field | Type | Notes |
|-------|------|-------|
| id | String | local uuid |
| inspectionId | String | FK (1:1) |
| json | String | canonical report JSON (see contracts) |
| localJsonPath | String? | |
| remoteJsonUrl | String? | |
| generatedAt | Long | |
| status | String | GENERATED/UPLOADED |
| syncState | SyncState | |

### AuditLogEntry
| Field | Type | Notes |
|-------|------|-------|
| id | String | local uuid |
| inspectionId | String | FK |
| eventType | String | CREATE/CAPTURE/SKIP/AI_RUN/FINALIZE/UPLOAD/... |
| actorId | String | inspector id |
| timestamp | Long | |
| detailJson | String? | event context |

### SyncTask
| Field | Type | Notes |
|-------|------|-------|
| id | String | local uuid |
| entityType | String | INSPECTION/IMAGE/FINDING/ANNOTATION/REPORT |
| entityId | String | |
| operation | String | UPSERT/DELETE/UPLOAD_FILE |
| status | SyncState | |
| attemptCount | Int | |
| lastAttemptAt | Long? | |
| lastError | String? | |

---

## Room schema (local — source of truth)

Tables (snake_case), one per entity above:
`inspectors`, `vehicles`, `inspections`, `inspection_images`, `ai_findings`,
`annotations`, `reports`, `audit_log`, `sync_tasks`, `checklist_responses`.

Key definitions:

- **Primary keys**: `id` (String) on every table.
- **Foreign keys** (with `onDelete = CASCADE` from parent):
  - `inspections.inspectorId → inspectors.id`, `inspections.vehicleId → vehicles.id`
  - `inspection_images.inspectionId → inspections.id`
  - `ai_findings.imageId → inspection_images.id`
  - `annotations.imageId → inspection_images.id`
  - `reports.inspectionId → inspections.id`
  - `audit_log.inspectionId → inspections.id`
  - `checklist_responses.inspectionId → inspections.id`
- **Indices**:
  - `inspections(inspectorId)`, `inspections(status)`, `inspections(syncState)`
  - `inspection_images(inspectionId)`, unique `inspection_images(inspectionId, section, position)`
  - `inspection_images(syncState)`, `inspection_images(aiState)`
  - `ai_findings(imageId)`, `annotations(imageId)`
  - `reports(inspectionId)`, `sync_tasks(status)`, `checklist_responses(inspectionId)`
- **TypeConverters**: enums ↔ String; timestamps as `Long`.
- **DAOs** return `Flow<...>` for observable reads (dashboard list, capture progress, sync
  status) and `suspend` for writes; per-image delete and per-inspection cascade delete are
  supported.
- **Migrations**: current schema is **v4**, migrated non-destructively from v1:
  - **v1→v2**: add `checklist_responses` table and `vehicles.odometerKm`; extend annotation
    damage-assessment fields.
  - **v2→v3**: add `inspection_images.checklistSectionId`.
  - **v3→v4**: add `inspection_images.checklistItemId`.
  Destructive fallback remains disabled in release.

Images and thumbnails are stored as encrypted files (app-internal storage); Room holds only
`localFilePath`/`thumbnailPath` references.

---

## Firestore schema

Documents keyed by local ids so uploads are idempotent. SDK types confined to `:core:data`.

```text
inspectors/{inspectorId}
  ├─ profile fields (displayName, email)
  └─ inspections/{inspectionId}
       ├─ context, status, currentStep, createdAt, updatedAt, completedAt
       ├─ gps { lat, lng }?, deviceInfo
       ├─ scores { exterior, interior, safety, cosmetic, confidence }, overallCondition
       ├─ finalRecommendation, summary
       ├─ vehicleCategory (NEW|OLD)
       ├─ vehicle { vin, category, year, manufacturer, make, model, variant, trim, bodyStyle,
       │            fuelType, transmission, color, registrationNumber,
       │            engineNumber?, chassisNumber?, numberOfOwnerships?, numberOfKeys?,
       │            vinInputMethod, decoded }
       ├─ images/{imageId}
       │    ├─ section (EXTERIOR|INTERIOR|DOCUMENT), position, documentType?, captureState, skipReason?
       │    ├─ remoteUrl, thumbnailUrl, width, height, sizeBytes, capturedAt, orientation, quality
       │    ├─ findings/{findingId}
       │    │    └─ damageType, confidence, severity, bbox{x,y,w,h},
       │    │       repairRecommendation, reviewRequired, source, createdAt
       │    └─ annotations/{annotationId}
       │         └─ shape, geometry, damageType, severity, comment?, createdAt, updatedAt
       ├─ report { remoteJsonUrl, generatedAt, status }
       └─ auditLog/{entryId}
            └─ eventType, actorId, timestamp, detail?
```

**Notes**:
- `vehicle` is embedded on the inspection doc for report locality; a top-level
  `vehicles/{vehicleId}` doc MAY also be written for cross-inspection lookup.
- Findings/annotations are subcollections to allow unbounded growth per image.
- All timestamps stored as epoch millis (Long) for consistency with the local model.

### Firestore Security Rules (intent)

- A document under `inspectors/{uid}/**` is readable/writable only when
  `request.auth.uid == uid`.
- App Check required for all reads/writes.
- Server-side validation of enum/string fields where feasible; client remains the
  authoritative validator for AI content before write.

---

## Firebase Storage hierarchy

```text
inspections/{inspectionId}/
  ├─ exterior/{position}.jpg
  ├─ interior/{position}.jpg
  ├─ documents/{documentType}.jpg      # Old vehicles: RC, POLLUTION_CERTIFICATE, INSURANCE
  ├─ thumbnails/{imageId}.jpg
  └─ report.json
```

- Deterministic paths (keyed by inspection + position/imageId) make uploads idempotent and
  resumable.
- Storage rules mirror Firestore ownership: only the owning inspector may read/write a
  given `inspections/{inspectionId}/**` path; App Check enforced.
- Images uploaded compressed; originals not retained beyond the compressed capture.
