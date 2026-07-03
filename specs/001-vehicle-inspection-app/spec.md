# Feature Specification: Android Vehicle Inspection Application

**Feature Branch**: `001-vehicle-inspection-app`

**Created**: 2026-07-01

**Status**: Draft

**Input**: User description: "Create a complete product specification for a production-ready Android Vehicle Inspection Application ... guide the inspector through a structured inspection process with AI-assisted damage detection."

## Overview

A native Android application that enables professional vehicle inspectors to perform
structured, guided, AI-assisted inspections of vehicles across multiple business
contexts: pre-delivery, rental, auction, insurance claim, service intake, resale, and
customer handover. The app walks the inspector through a fixed, position-by-position
capture flow for the vehicle exterior and interior, validates image quality on-device,
runs AI damage detection on every image, lets the inspector manually annotate findings,
performs a final AI verification across the entire inspection, and produces a structured,
shareable inspection report. The app is offline-first: an inspection can be completed end
to end without connectivity, with images and data synced to the cloud automatically when
a connection becomes available.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Complete a guided end-to-end inspection (Priority: P1)

An authenticated inspector starts a new inspection from the dashboard, identifies the
vehicle, captures all required exterior and interior positions with on-device quality
validation, reviews the collected media, and finalizes the inspection so a report is
produced. This is the core value of the product.

**Why this priority**: Without a reliable, guided capture-to-report flow, the product
delivers no value. This is the minimum viable product.

**Independent Test**: Log in with a valid account, start an inspection, enter the VIN and
select New/Old, complete vehicle identification (manual entry), capture the mandatory
exterior and interior positions in sequence (or skip with reason), reach Review, finalize,
and confirm a report is generated and viewable — all on a single device, offline.

**Acceptance Scenarios**:

1. **Given** an authenticated inspector on the dashboard, **When** they tap "Start
   Inspection", **Then** a new inspection is created in a draft state and the inspector is
   prompted to enter the VIN and select whether the vehicle is New or Old.
2. **Given** the inspector is on the exterior or interior capture wizard, **When** a
   position is captured or skipped-with-reason, **Then** the wizard advances strictly to
   the next position in the defined sequence order (no arbitrary skipping ahead).
3. **Given** an in-progress exterior capture step, **When** the inspector captures a
   required position with acceptable quality, **Then** the position is marked complete
   and the flow advances to the next required position.
3. **Given** a required position the inspector cannot capture, **When** they choose to
   skip it, **Then** the app requires a skip reason before allowing progression.
4. **Given** all mandatory positions are captured or skipped-with-reason, **When** the
   inspector opens Review, **Then** the app shows completeness status and allows
   finalization only when no mandatory item is unaddressed.
5. **Given** a finalized inspection, **When** report generation completes, **Then** the
   inspector can view a report containing vehicle details, images, findings, and an
   overall recommendation.

---

### User Story 2 - AI-assisted damage detection on captured images (Priority: P1)

For every captured exterior and interior image, the system analyzes the image and
returns detected damages with type, confidence, severity, bounding box, a repair
recommendation, and a flag indicating whether inspector review is required.

**Why this priority**: AI-assisted detection is the core differentiator and directly
drives inspection accuracy and speed. It is essential to the primary value proposition.

**Independent Test**: Capture (or import) an image of a damaged panel, trigger analysis,
and confirm the app returns one or more findings each with type, confidence, severity,
and a bounding box overlay, and that low-confidence findings are flagged for review.

**Acceptance Scenarios**:

1. **Given** a captured exterior image, **When** analysis runs, **Then** the app displays
   detected damages with type, confidence, severity, and bounding boxes overlaid on the
   image.
2. **Given** an analysis result below the confidence threshold, **When** results are
   shown, **Then** the finding is marked "Inspector Review Required".
3. **Given** an AI response is received, **When** the app processes it, **Then** the
   response is validated against the expected schema before any finding is stored or
   displayed as authoritative; malformed or invalid responses are rejected with a safe
   fallback and a retry option.
4. **Given** no network connectivity, **When** an image is captured, **Then** analysis is
   queued and the capture flow continues without blocking; analysis runs when
   connectivity is restored.

---

### User Story 3 - Vehicle identification & New/Old classification (Priority: P2)

The inspector enters the VIN (manually or via camera OCR) and classifies the vehicle as
New or Old. For an Old vehicle, the inspector must enter the registration number. On
successful VIN recognition, the app displays decoded vehicle attributes; on failure, the
inspector manually selects vehicle attributes.

**Why this priority**: Accurate vehicle identity and New/Old classification anchor the
entire inspection record and determine which additional steps (Old-vehicle documentation)
are required, but a manual fallback keeps the flow usable.

**Independent Test**: Start an inspection, enter a VIN and pick New → proceed without a
registration number; pick Old → confirm the registration number becomes required; enter a
VIN manually and confirm decoded attributes populate; scan a VIN label and confirm OCR
extracts the VIN; force a lookup failure and confirm manual attribute selection is
available.

**Acceptance Scenarios**:

1. **Given** the start-inspection step, **When** the inspector enters a valid VIN and
   selects New or Old, **Then** the classification is recorded on the inspection.
2. **Given** the vehicle is classified as Old, **When** the inspector proceeds, **Then**
   the app requires a registration number before allowing progression.
3. **Given** the vehicle is classified as New, **When** the inspector proceeds, **Then**
   the registration number and Old-vehicle documentation steps are not required.
4. **Given** a valid VIN entered manually, **When** the inspector confirms, **Then** the
   app attempts a lookup and displays decoded attributes for confirmation.
5. **Given** a VIN label in the camera view, **When** the inspector scans it, **Then**
   the app extracts the VIN via OCR and pre-fills the VIN field for confirmation.
6. **Given** a VIN lookup that fails or returns no data, **When** the failure occurs,
   **Then** the app lets the inspector manually select manufacturer, model, variant,
   year, and color and proceed.
7. **Given** confirmed vehicle details, **When** the inspector proceeds, **Then** the
   details are saved to the inspection record and (when online) persisted to the backend.

---

### User Story 4 - Manual annotation of findings (Priority: P2)

The inspector manually annotates any image by drawing rectangles, freehand shapes, or pin
markers; assigns a damage type and severity (Low/Medium/High/Critical); and adds
comments. Annotations are unlimited per image.

**Why this priority**: Manual annotation guarantees the inspector can record findings the
AI misses or correct AI findings, ensuring report accuracy and inspector authority.

**Independent Test**: Open a captured image, draw a rectangle and a pin marker, assign a
damage type and severity, add a comment, and confirm all annotations persist with the
image.

**Acceptance Scenarios**:

1. **Given** a captured image, **When** the inspector draws a rectangle, freehand shape,
   or places a pin, **Then** the annotation is added and remains editable.
2. **Given** a new annotation, **When** the inspector selects a damage type and one of
   Low/Medium/High/Critical severity and enters a comment, **Then** the annotation stores
   all of these attributes.
3. **Given** multiple annotations on one image, **When** the inspector adds more, **Then**
   the app imposes no fixed limit on the number of annotations.

---

### User Story 5 - Manual-annotation AI re-verification (Priority: P3)

After the inspector annotates an area, the AI rechecks the marked region to confirm the
damage, suggest corrections, identify additional nearby damage, merge overlapping
detections, and flag inconsistencies between manual and AI findings.

**Why this priority**: Improves accuracy and reduces disputes, but the inspection is
already valuable with manual annotation and initial AI detection.

**Independent Test**: Annotate a region on an image, trigger re-verification, and confirm
the app returns a confirmation/correction, any nearby detections, merged overlaps, and an
inconsistency flag where manual and AI findings disagree.

**Acceptance Scenarios**:

1. **Given** a manually annotated region, **When** re-verification runs, **Then** the app
   returns a confirmation or a suggested correction for that region.
2. **Given** overlapping manual and AI detections, **When** re-verification runs, **Then**
   overlapping detections are merged into a single finding.
3. **Given** a conflict between manual and AI classification, **When** re-verification
   runs, **Then** the app flags the inconsistency for the inspector to resolve.

---

### User Story 6 - Final AI verification and report generation (Priority: P2)

When the inspection is complete, the app runs AI across all collected images to produce
overall condition and category scores, an inspection summary, and integrity flags
(missing/duplicate/low-quality/suspicious images, potential fraud), then generates a
structured report.

**Why this priority**: Turns raw findings into a decision-ready deliverable and integrity
assurance, which is central to the business use cases (auction, insurance, resale).

**Independent Test**: Finalize an inspection and confirm the app produces an overall
condition, exterior/interior/safety/cosmetic/confidence scores, a summary, integrity
flags, and a structured report.

**Acceptance Scenarios**:

1. **Given** a completed capture set, **When** final verification runs, **Then** the app
   produces overall vehicle condition plus exterior, interior, safety, cosmetic, and
   confidence scores.
2. **Given** the collected image set, **When** integrity checks run, **Then** the app
   flags missing, duplicate, low-quality, suspicious images, and potential fraud.
3. **Given** completed verification, **When** report generation runs, **Then** a
   structured report is produced containing vehicle, inspector, timing, images, findings,
   annotations, scores, and a final recommendation.

---

### User Story 7 - Offline-first capture with automatic sync (Priority: P2)

An inspector performs a complete inspection without connectivity; all data and images are
stored locally, then uploaded automatically (with retry and background upload) when
connectivity is restored, with clear sync status per item.

**Why this priority**: Inspections routinely happen in lots, garages, and basements with
no signal; offline reliability is a hard product requirement.

**Independent Test**: Enable airplane mode, complete an inspection end to end, re-enable
connectivity, and confirm images and data upload automatically with visible sync status
and successful retry of any failed uploads.

**Acceptance Scenarios**:

1. **Given** no connectivity, **When** the inspector captures images and enters data,
   **Then** everything is stored locally and the app remains fully functional.
2. **Given** connectivity is restored, **When** the app detects the connection, **Then**
   pending items upload automatically in the background.
3. **Given** an upload failure, **When** the app retries, **Then** it uses backoff and
   surfaces per-item sync state (pending, uploading, synced, failed).

---

### User Story 8 - Authentication and dashboard (Priority: P1)

An inspector logs in securely and lands on a dashboard that lists their inspections
(in-progress, pending sync, completed) and provides an entry point to start a new
inspection.

**Why this priority**: Access control and a home surface are prerequisites for all other
flows and for attributing inspections to an inspector.

**Independent Test**: Log in with valid credentials, land on the dashboard, see the list
of inspections and their statuses, and start a new inspection.

**Acceptance Scenarios**:

1. **Given** the login screen, **When** the inspector submits valid credentials, **Then**
   they are authenticated and taken to the dashboard.
2. **Given** invalid credentials, **When** the inspector submits, **Then** an error is
   shown and access is denied.
3. **Given** an authenticated inspector on the dashboard, **When** the screen loads,
   **Then** it displays their inspections grouped by status and a "Start Inspection"
   action.

---

### User Story 9 - Old-vehicle documentation & details (Priority: P2)

When the vehicle is classified as Old, the inspector must capture registration-related
documents and enter ownership/key details before proceeding to the exterior/interior
capture: a photo of the RC (Registration Certificate), a photo of the Pollution
Certificate (PUC), a photo of the Insurance, the number of previous ownerships, and the
number of keys.

**Why this priority**: Old vehicles (rental, auction, insurance, resale, handover) require
documentary evidence and provenance details that materially affect valuation and the
inspection's legal/business value.

**Independent Test**: Classify a vehicle as Old, then confirm the app requires a photo of
RC, Pollution Certificate, and Insurance, plus number-of-ownerships and number-of-keys,
before allowing progression to exterior capture; classify a vehicle as New and confirm
these steps are skipped.

**Acceptance Scenarios**:

1. **Given** an Old vehicle, **When** the inspector reaches the additional-details step,
   **Then** the app requires photos of the RC, Pollution Certificate, and Insurance.
2. **Given** an Old vehicle, **When** the inspector enters the additional details, **Then**
   the app requires a number of ownerships and a number of keys.
3. **Given** any required Old-vehicle document or detail is missing, **When** the inspector
   attempts to proceed, **Then** progression is blocked until it is provided (or skipped
   with a reason, consistent with the capture rules).
4. **Given** a New vehicle, **When** the inspector proceeds, **Then** the Old-vehicle
   documentation and details step is not shown.
5. **Given** captured Old-vehicle documents and details, **When** the inspector proceeds,
   **Then** they are stored with the inspection, included in the report, and synced when
   online.

---

### Edge Cases

- **Login while offline**: Cached/last-valid session allows offline access to existing
  local inspections; a fresh first-time login requires connectivity.
- **VIN OCR ambiguity**: OCR returns a low-confidence or malformed VIN → inspector is
  asked to confirm/correct before lookup.
- **VIN lookup returns partial data**: Missing fields are left editable for manual entry;
  optional fields (engine number, chassis number) may be omitted.
- **Duplicate VIN / existing inspection**: App warns if an active inspection already
  exists for the same VIN and offers to resume or start new.
- **Camera/permission denied**: App explains why the permission is needed and provides a
  path to settings; capture steps are blocked until granted.
- **Insufficient storage**: App warns before capture and prevents data loss if storage
  fills mid-inspection.
- **Blurry / dark / overexposed / incomplete image**: On-device quality validation
  rejects the image and prompts recapture with specific guidance.
- **AI service unavailable or timeout**: Analysis is queued/retried; the inspection can
  still be completed and finalized with findings marked pending AI.
- **Invalid or malformed AI response**: Response fails validation → rejected, safe
  fallback applied, retried; never displayed as authoritative or persisted.
- **Partial upload / app killed mid-sync**: Sync resumes from the last successful item;
  no duplicate uploads.
- **Skipping mandatory positions**: Requires a reason; report clearly flags skipped items.
- **New vs Old classification**: Selecting Old requires a registration number and the
  Old-vehicle documentation step; selecting New skips both. Changing the classification
  mid-inspection re-evaluates which steps/fields are required.
- **Missing Old-vehicle documents**: RC / Pollution Certificate / Insurance photos and
  ownership/key counts are required for Old vehicles before capture proceeds (or explicitly
  skipped with a reason, flagged in the report).
- **Out-of-sequence capture**: The exterior/interior wizard enforces the defined sequence
  order; the inspector cannot jump ahead past an unaddressed mandatory position.
- **Interrupted inspection (call, battery, crash)**: Draft state is auto-saved and
  resumable from the dashboard.
- **Clock/timezone anomalies**: Inspection timestamps recorded in a consistent, auditable
  form.
- **Time-zone / localization**: All user-facing text and formats respect device locale.
- **Fraud indicators**: Reused/manipulated images or metadata mismatches are flagged, not
  silently accepted.

## Requirements *(mandatory)*

### Functional Requirements

#### Authentication & Session

- **FR-001**: System MUST authenticate inspectors before granting access to any
  inspection data.
- **FR-002**: System MUST maintain an authenticated session that permits offline access
  to locally stored inspections after a successful prior login.
- **FR-003**: System MUST deny access and show a clear error on invalid credentials.

#### Dashboard

- **FR-004**: System MUST present a dashboard listing the inspector's inspections grouped
  by status (in-progress, pending sync, completed).
- **FR-005**: System MUST provide a "Start Inspection" action that creates a new draft
  inspection.
- **FR-006**: System MUST allow resuming any in-progress (draft) inspection.

#### Inspection Flow

- **FR-007**: System MUST guide the inspector through the ordered flow: Login → Dashboard
  → Start Inspection (VIN + New/Old) → Identify Vehicle → Vehicle Details → [Old-vehicle
  Documentation & Details, if Old] → Exterior Inspection → Interior Inspection → Review →
  AI Final Verification → Generate Report → Upload.
- **FR-008**: System MUST persist inspection progress continuously so no data is lost on
  interruption, and MUST allow resumption from the last state.
- **FR-008a**: The exterior and interior inspections MUST be presented as a step-by-step
  wizard that enforces the defined sequence order of positions; the inspector MUST NOT be
  able to advance past an unaddressed mandatory position (captured or skipped-with-reason).

#### Vehicle Identification & Classification

- **FR-009**: System MUST support manual VIN entry.
- **FR-010**: System MUST support VIN capture via the camera using on-device OCR.
- **FR-011**: System SHOULD support barcode/QR-code VIN capture (planned future feature;
  out of scope for MVP).
- **FR-012**: On successful VIN recognition, System MUST display VIN, Year, Manufacturer,
  Make, Model, Variant, Trim, Body Style, Fuel Type, Transmission, Color, and
  Registration Number, and MUST allow optional Engine Number and Chassis Number.
- **FR-013**: On VIN lookup failure, System MUST allow manual selection of Manufacturer,
  Model, Variant, Year, and Color.
- **FR-014**: System MUST let the inspector confirm/correct all identification fields
  before proceeding.
- **FR-015**: System MUST store confirmed vehicle details with the inspection and persist
  them to the backend when connectivity is available.
- **FR-015a**: At the start of an inspection, System MUST capture the VIN and require the
  inspector to classify the vehicle as New or Old.
- **FR-015b**: When the vehicle is classified as Old, System MUST require a registration
  number before allowing progression; when New, the registration number is not required at
  this step.

#### Old-Vehicle Documentation & Details

- **FR-015c**: When the vehicle is classified as Old, System MUST require, before exterior
  capture, a photo of the RC (Registration Certificate), a photo of the Pollution
  Certificate (PUC), and a photo of the Insurance.
- **FR-015d**: When the vehicle is classified as Old, System MUST require the inspector to
  enter the number of previous ownerships and the number of keys.
- **FR-015e**: System MUST block progression past the Old-vehicle documentation step until
  all required documents and details are provided, or explicitly skipped with a reason
  (flagged in the report), consistent with capture rules.
- **FR-015f**: When the vehicle is classified as New, System MUST NOT require the
  Old-vehicle documentation and details step.
- **FR-015g**: System MUST store Old-vehicle documents and details with the inspection,
  include them in the report, and sync them when connectivity is available.

#### Exterior Capture

- **FR-016**: System MUST guide the inspector through all 25 defined exterior positions
  (Front; Front Left Corner 45°; Left Side; Rear Left Corner; Rear; Rear Right Corner;
  Right Side; Front Right Corner; Roof; Bonnet/Hood; Windshield Close-up; Rear
  Windshield; Front Left Wheel; Front Right Wheel; Rear Left Wheel; Rear Right Wheel;
  Driver Door Close-up; Passenger Door Close-up; Fuel Cap Area; Front Bumper Close-up;
  Rear Bumper Close-up; Headlights; Tail Lights; Number Plate; Underbody). Underbody is
  optional; all others are mandatory unless skipped with a reason.
- **FR-017**: Each capture screen MUST display an example image, an outline/overlay,
  angle guidance, and distance guidance for the target position.
- **FR-018**: System MUST validate on-device image quality and MUST reject blurry, dark,
  overexposed, or incomplete-vehicle images, prompting recapture with specific reason.
- **FR-019**: System MUST require a skip reason to bypass any mandatory position.

#### Interior Capture

- **FR-020**: System MUST guide the inspector through all 25 defined interior positions
  (Dashboard; Steering Wheel; Instrument Cluster; Odometer; Infotainment Display; Gear
  Lever; Center Console; Driver Seat; Passenger Seat; Rear Seats; Roof Lining; Floor
  Mats; Driver Door Trim; Passenger Door Trim; Rear Door Trim; Boot/Trunk; Spare Wheel;
  Toolkit; AC Controls; Pedals; Seat Belts; Sunroof [if available]; Glove Box; Cup
  Holders; Cargo Area).
- **FR-021**: Interior capture screens MUST provide the same guidance and quality
  validation as exterior capture (FR-017, FR-018).

#### AI Damage Detection (Exterior & Interior)

- **FR-022**: For every exterior image, System MUST analyze for the defined exterior
  damage taxonomy (e.g., Dent, Scratch, Deep Scratch, Paint Chip, Paint Peel, Rust,
  Crack, Broken Plastic/Glass/Headlight/Tail Light, Mirror Damage, Missing/Loose Parts,
  Panel Gap/Misalignment, Paint Mismatch, Aftermarket Repair Evidence, Corrosion, Wheel
  Damage/Scratch/Crack, Tyre Wear/Bulge/Low Tread, Bent Rim, Oil/Fluid Leak, Broken
  Badge/Number Plate/Grill/Bumper, Fog Lamp Damage, Stone Chips, Bird Droppings, Heavy
  Dirt).
- **FR-023**: For every interior image, System MUST analyze for the defined interior
  damage taxonomy (e.g., Seat Tear/Stain/Burn, Dashboard Crack/Scratch, Steering
  Wear/Damage, Broken/Missing Buttons, Broken AC Vent, Loose Trim, Broken Plastic/Display,
  Dead Pixels, Broken Switch/Mirror Control, Water Damage, Mold, Excessive Dirt, Bad Odor
  Indicators, Floor/Roof Liner/Door Panel Damage, Missing Accessories, Broken Seat
  Belt/Arm Rest/Storage, Cracked Console).
- **FR-024**: Each AI finding MUST include damage type, confidence, severity, bounding
  box, repair recommendation, and an "Inspector Review Required" flag.
- **FR-025**: System MUST validate every AI response against an expected schema before use
  and MUST reject, safely fall back on, and offer retry for any invalid/malformed
  response. Unvalidated AI output MUST NOT be persisted or shown as authoritative.
- **FR-026**: System MUST flag findings below a defined confidence threshold as requiring
  inspector review.
- **FR-027**: System MUST queue analysis when offline and run it when connectivity
  returns, without blocking capture.

#### Manual Annotation

- **FR-028**: System MUST allow the inspector to annotate any image with rectangles,
  freehand shapes, and pin markers, including damage-specific highlights (dent, scratch,
  crack).
- **FR-029**: Each annotation MUST support a damage type, a severity of Low/Medium/High/
  Critical, and free-text comments.
- **FR-030**: System MUST allow an unlimited number of annotations per image.
- **FR-031**: System SHOULD support voice comments (planned future feature; out of scope
  for MVP).

#### Annotation Re-verification

- **FR-032**: After a manual annotation, System MUST let the AI recheck the marked area,
  confirm the damage, suggest corrections, identify additional nearby damage, merge
  overlapping detections, and flag inconsistencies between manual and AI findings.

#### Final Verification

- **FR-033**: On inspection completion, System MUST run AI over all collected images and
  produce Overall Vehicle Condition plus Exterior, Interior, Safety, Cosmetic, and
  Confidence scores, and an inspection summary.
- **FR-034**: System MUST flag missing images, duplicate images, low-quality images,
  suspicious images, and potential fraud.

#### Reporting

- **FR-035**: System MUST generate a structured (JSON) report containing vehicle details,
  inspector, inspection time, optional GPS, device information, all images (thumbnail,
  image URL, image metadata), manual annotations, AI findings (with confidence, severity,
  bounding boxes), comments, final recommendation, vehicle condition, and inspection
  status.
- **FR-036**: System MUST provide a report screen displaying vehicle summary, inspection
  timeline, exterior gallery, interior gallery, damage list, damage count, AI findings,
  inspector notes, overall score, and final recommendation.
- **FR-037**: System SHOULD allow the report to be shared/exported.

#### Storage & Sync

- **FR-038**: System MUST function offline for the entire inspection lifecycle up to
  report generation, using a local store as the source of truth.
- **FR-039**: System MUST automatically sync inspections, images, metadata, annotations,
  AI results, and reports to the backend when connectivity is available, including
  background upload and retry with backoff.
- **FR-040**: System MUST compress images before upload while preserving analysis quality.
- **FR-041**: System MUST display per-item sync status (pending, uploading, synced,
  failed).
- **FR-042**: System MUST persist Vehicle, Inspection, Images, Image Metadata,
  Annotations, AI Results, JSON Report, and an Audit Log to the backend.

#### Cross-cutting

- **FR-043**: System MUST record an audit log of significant inspection events (creation,
  captures, skips, AI runs, finalization, uploads).
- **FR-044**: System MUST request and gracefully handle runtime permissions (camera,
  microphone for future voice, location for optional GPS, storage/media as applicable).
- **FR-045**: System MUST be accessible (screen-reader semantics, sufficient contrast,
  adequate touch targets, dynamic font scaling).
- **FR-046**: System MUST support dark mode, tablet layouts, landscape orientation, and
  localization.
- **FR-047**: System MUST report crashes and capture usage analytics.
- **FR-048**: System MUST encrypt sensitive data at rest on device and use secure
  communication for all backend and AI interactions.

#### Checklist-driven workflow & enhancements (added 2026-07-03)

- **FR-049**: System MUST present a catalog-driven inspection checklist as the primary
  interface after vehicle identification (checklist-first), with sections/items filtered by
  New/Old applicability. Photo capture and damage marking are performed from within the
  checklist rather than a separate linear capture wizard.
- **FR-050**: For New vehicles the app MUST skip the dedicated document-capture step and load
  the checklist directly; Old-vehicle documents are captured within the checklist's Documents
  section (as photo-capable items).
- **FR-051**: System MUST intelligently mark which checklist items warrant photo evidence
  (`photoCapable`) — e.g. body panels, wear, leaks, tyres, documents — and expose per-item
  image capture only for those items; purely functional checks have no photo grid.
- **FR-052**: Each photo-capable checklist item MUST maintain its own distinct set of images
  (tagged by `checklistItemId`), shown in a per-item image grid with a configurable maximum
  (`MAX_IMAGES_PER_ITEM`, default 10). Tapping an image opens the Image Detail view.
- **FR-053**: Component condition MUST be recorded with granular, context-aware grades
  (Good, Minor scratches/wear, Major scratches/wear, Damage, N/A), where the wording adapts
  to the inspection area (e.g. "wear" for interior vs "scratches" for exterior).
- **FR-054**: The Add-photo screen MUST allow continuous capture (multiple photos in one
  visit) until the user presses back, showing the running total, remaining allowance, and
  session count; on back it MUST prompt to keep or discard the photos captured that session.
- **FR-055**: System MUST let the inspector delete an individual captured image from the
  inspection (per-item grid) with confirmation, removing both the database record and the
  on-disk file.
- **FR-056**: System MUST let the inspector delete an entire inspection with confirmation,
  cascading removal of all associated images, findings, annotations, checklist responses,
  reports, audit logs, and on-disk files.
- **FR-057**: Image type labels MUST show the full checklist path
  (Section → Group → Item, e.g. "Exterior Inspection → Front → Front Bumper"), and the Image
  Detail gallery MUST be scoped to the selected item/inspection.
- **FR-058**: The Image Detail gallery MUST support swipe navigation scoped to the current
  checklist item (falling back to section/inspection) in capture-sequence order.
- **FR-059**: Final recommendation MUST support six options: No repair, Cosmetic repair,
  Mechanical service, Body-shop repair, Insurance claim, Reject vehicle.
- **FR-060**: The JSON report MUST nest each photo under its corresponding checklist item
  (with a human-readable `checklistItem` label); only untagged/legacy captures remain in the
  flat top-level `images[]`. The report MUST also include the checklist, damage assessment,
  and final assessment blocks.
- **FR-061**: System MUST produce a branded, multi-section PDF report (cover, contents,
  at-a-glance, category ratings summary, per-category parameter tables with perfect/imperfect
  counts, photo gallery, and damage-evidence pages with AI/manual marks) generated on demand
  from the stored inspection graph.
- **FR-062**: PDF export MUST bound its file size via configurable JPEG quality
  (`PDF_IMAGE_QUALITY`), embedded image widths (`PDF_GALLERY_IMAGE_WIDTH`,
  `PDF_DAMAGE_IMAGE_WIDTH`), and a total-image cap (`PDF_MAX_IMAGES`), and MUST embed each
  photo at most once (marked photos in damage evidence, the rest in the gallery).

### Key Entities *(include if feature involves data)*

- **Inspector**: The authenticated user performing inspections. Attributes: identity,
  display name, assigned inspections. Relationship: owns many Inspections.
- **Vehicle**: The subject of an inspection. Attributes: VIN, category (New/Old), year,
  manufacturer, make, model, variant, trim, body style, fuel type, transmission, color,
  registration number (required for Old), optional engine number, optional chassis number,
  and — for Old vehicles — number of previous ownerships and number of keys. Relationship:
  referenced by Inspections.
- **Inspection**: A single inspection session. Attributes: context/purpose, vehicle
  category (New/Old), status (draft/in-progress/completed/synced), timestamps, optional
  GPS, device info, scores, final recommendation. Relationships: belongs to an Inspector,
  references a Vehicle, has many Images (including Old-vehicle documents), has many
  Annotations, has one Report, has many Audit Log entries.
- **InspectionImage**: A captured image at a defined position. Attributes: position/label,
  section (exterior/interior/document), quality status, skip reason (if skipped),
  thumbnail, storage URL, metadata (resolution, capture time, orientation, size).
  Relationship: belongs to an Inspection, has many AI Findings and Annotations. Old-vehicle
  documents (RC, Pollution Certificate, Insurance) are represented as document-section
  images.
- **AIFinding**: A detected damage. Attributes: damage type, confidence, severity,
  bounding box, repair recommendation, inspector-review-required flag, source
  (initial/re-verification). Relationship: belongs to an InspectionImage.
- **Annotation**: A manual finding on an image. Attributes: shape type (rectangle/
  freehand/pin), geometry, damage type, severity (Low/Medium/High/Critical), comments.
  Relationship: belongs to an InspectionImage.
- **Report**: The generated deliverable. Attributes: structured JSON payload, scores,
  final recommendation, generation time, status. Relationship: belongs to an Inspection.
- **AuditLogEntry**: A recorded event. Attributes: event type, actor, timestamp, context.
  Relationship: belongs to an Inspection.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An inspector can complete a full guided inspection (identification through
  report generation) for a typical vehicle in under 20 minutes.
- **SC-002**: 100% of mandatory exterior and interior positions are enforced — an
  inspection cannot be finalized with an unaddressed mandatory position (captured or
  skipped-with-reason).
- **SC-003**: A full inspection can be completed with zero connectivity, and 100% of
  locally captured data and images successfully sync once connectivity is restored.
- **SC-004**: On-device quality validation rejects blurry, dark, overexposed, or
  incomplete images before they enter the inspection, with a clear recapture prompt.
- **SC-005**: Every stored/displayed AI finding has passed response validation; no invalid
  AI output is ever presented as authoritative.
- **SC-006**: Every AI finding presented to the inspector includes type, confidence,
  severity, and a bounding box, and low-confidence findings are visibly flagged for
  review.
- **SC-007**: A finalized inspection always produces a structured report containing all
  required sections (vehicle, inspector, timing, images, findings, annotations, scores,
  recommendation).
- **SC-008**: The app remains responsive during capture, with no perceptible UI freeze
  when saving images or queuing analysis.
- **SC-009**: An interrupted inspection (crash, battery, call) is fully resumable from the
  last saved state with no data loss.
- **SC-010**: All primary screens pass accessibility checks (screen-reader labels,
  contrast, touch-target size) and render correctly in dark mode, landscape, and on
  tablets.

## Assumptions

- **Authentication** uses standard account-based sign-in; first-time sign-in requires
  connectivity, subsequent access can be offline via a cached session.
- **VIN decoding** relies on a VIN-decode data source/service; when unavailable or
  incomplete, manual attribute entry is the fallback.
- **AI analysis requires connectivity**; when offline, analysis is queued and executed on
  reconnect, and the inspection can still be finalized with findings marked pending.
- **Confidence threshold** for "Inspector Review Required" is a configurable value with a
  sensible default.
- **GPS and device information** are captured when permitted; GPS is optional.
- **Barcode/QR VIN capture and voice comments** are explicitly future features and out of
  MVP scope.
- **Image retention and privacy** follow industry-standard practices appropriate to
  inspection records; retention specifics are configurable by the operating organization.
- **Report format** is JSON as the canonical structure; human-readable/exportable
  presentation is derived from it.
- **A single inspector** owns an inspection; multi-inspector collaboration is out of scope
  for this version.

## Design Intent & Acceptance Artifacts

> The following describe intended behavior and structure at a requirements level to
> satisfy the acceptance criteria. Detailed technical design (concrete schemas, diagrams,
> and contracts) is produced during the planning phase (`/speckit-plan`).

### User Journeys (high level)

- **Primary journey**: Login → Dashboard → Start Inspection (enter VIN + select New/Old;
  Old ⇒ registration number) → Identify Vehicle → confirm Vehicle Details → [Old only:
  Old-vehicle Documentation & Details — RC/PUC/Insurance photos, #ownerships, #keys] →
  Exterior Inspection (25 positions, sequence-ordered wizard, guided + validated + AI) →
  Interior Inspection (25 positions, sequence-ordered wizard, guided + validated + AI) →
  Review (completeness, annotations, edits) → AI Final Verification (scores + integrity
  flags) → Generate Report → Upload/Sync.
- **Old-vehicle journey**: classify as Old → provide registration + RC/PUC/Insurance photos
  + ownership/key counts → continue to capture.
- **New-vehicle journey**: classify as New → skip registration/documentation → continue to
  capture.
- **Recovery journey**: Resume a draft inspection from the dashboard after interruption.
- **Offline journey**: Complete an inspection with no connectivity; sync automatically
  later with visible status and retry.
- **Fallback journey**: VIN lookup fails → manual vehicle attribute selection → continue.

### Screen & Wireframe Descriptions (intent)

- **Login**: Credential entry, error messaging, offline-session indicator.
- **Dashboard**: Sectioned list of inspections by status; prominent "Start Inspection";
  per-item sync badges.
- **Start Inspection**: VIN entry field + "Scan VIN" action, New/Old segmented selector;
  when Old, a required registration-number field appears.
- **Identify Vehicle**: VIN entry field, "Scan VIN" camera action, lookup status, decoded
  attribute review, manual fallback.
- **Vehicle Details**: Editable/confirmable attribute form (required + optional fields).
- **Old-vehicle Documentation & Details** (Old only): capture tiles for RC, Pollution
  Certificate, and Insurance photos; numeric inputs for number of ownerships and number of
  keys; progression gated until required items are provided (or skipped with reason).
- **Capture (Exterior/Interior)**: Sequence-ordered wizard (one position at a time). Live
  camera with example image, outline overlay, angle and distance guidance, capture button,
  quality feedback, "Skip with reason", progress indicator (n of total), thumbnail strip.
- **Image Detail / Annotation**: Full image with AI bounding-box overlays; annotation
  tools (rectangle, freehand, pin); damage type + severity pickers; comment field;
  re-verify action.
- **Review**: Completeness matrix (captured/skipped/pending AI), galleries, damage list,
  edit access, finalize action (gated by completeness).
- **AI Final Verification**: Progress, resulting scores (overall/exterior/interior/safety/
  cosmetic/confidence), integrity flags, summary.
- **Report**: Vehicle summary, timeline, exterior gallery, interior gallery, damage list +
  count, AI findings, inspector notes, overall score, final recommendation, share/export.

### Navigation Graph (intent)

Login → Dashboard. Dashboard → New Inspection (Start Inspection) and Dashboard → Resume
(last step) and Dashboard → Report (for completed). Inspection is a linear, resumable,
sequence-ordered wizard (Start Inspection [VIN + New/Old] → Identify Vehicle → Vehicle
Details → [Old-vehicle Documentation, if Old] → Exterior → Interior → Review → Final
Verification → Report) with back navigation and the ability to jump to any captured image
from Review. New vehicles bypass the Old-vehicle Documentation step. Report → share/export
and Report → Dashboard.

### State Management (intent)

Each screen is driven by a single immutable UI state exposed by a view model as an
observable stream; user intents are dispatched to the view model, which updates state and
delegates logic to use cases. Inspection progress is persisted to the local store as the
source of truth; sync state is a derived, observable property per item.

### Data & Schema Intent

- **Local store**: tables/collections for Inspector, Vehicle, Inspection,
  InspectionImage, AIFinding, Annotation, Report, AuditLogEntry, and a SyncQueue for
  pending uploads. Images stored as files with references held in the store.
- **Backend collections**: Vehicle, Inspection, Images metadata, Image Metadata,
  Annotations, AI Results, JSON Report, Audit Log — keyed by inspection and vehicle
  identifiers.
- **Object storage hierarchy (intent)**: organized per inspection, e.g.
  `inspections/{inspectionId}/exterior/{position}.jpg`,
  `inspections/{inspectionId}/interior/{position}.jpg`,
  `inspections/{inspectionId}/thumbnails/...`, with report artifacts under
  `inspections/{inspectionId}/report.json`.
- **Report JSON**: canonical structure containing vehicle, inspector, timing, GPS, device,
  images (thumbnail, URL, metadata), annotations, AI findings (confidence, severity,
  bounding boxes), comments, scores, final recommendation, and status.

### API / Contract Intent

- **Authentication**: sign-in and session validation.
- **VIN decode**: given a VIN, return decoded vehicle attributes (or not-found).
- **AI damage detection**: given an image and section/position context, return a validated
  set of findings (type, confidence, severity, bounding box, recommendation, review flag).
- **AI re-verification**: given an image and an annotated region, return confirmation/
  correction, nearby detections, merged overlaps, and inconsistency flags.
- **AI final verification**: given the full image set, return scores, summary, and
  integrity flags.
- **Sync/upload**: upload images, metadata, annotations, AI results, and report with
  idempotent, resumable semantics.

### Sequence Intent (key flows)

- **Capture → Validate → Analyze**: capture image → on-device quality check → accept or
  prompt recapture → persist locally → queue AI analysis → on result, validate schema →
  store findings or fall back/retry.
- **Finalize**: gate on completeness → run final AI verification → compute scores +
  integrity flags → generate report → enqueue upload.
- **Sync**: detect connectivity → drain sync queue in order → retry failed items with
  backoff → update per-item status.

### Permission Handling (intent)

Camera (required for capture and VIN OCR), location (optional for GPS), microphone
(future voice comments), and media/storage as applicable. Each permission is requested in
context with a rationale, and denial is handled gracefully with guidance to re-enable.

### Testing Strategy (intent)

- Unit tests for every use case (success, failure, boundary), including AI-response
  validation.
- UI tests for every screen, including accessibility semantics and the capture/skip flow.
- Integration tests for offline capture, sync/retry, and report generation.
- Validation tests that malformed AI responses are rejected and never surfaced as
  authoritative.

### Security Considerations (intent)

Encrypt sensitive data at rest on device; secure all backend and AI communications;
enforce authentication and per-inspector access; validate all external/AI input; maintain
an audit log; and treat images/metadata as sensitive inspection records.

### Performance Targets (intent)

Smooth capture with no perceptible UI freeze; on-device quality validation returns quickly
enough to keep the capture cadence; image compression keeps uploads efficient without
degrading analysis; background sync does not block interactive use.

### Future Enhancements

Barcode/QR VIN capture; voice comments; multi-inspector collaboration; additional AI
damage categories; export to PDF and third-party integrations.
