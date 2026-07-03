package com.vsp.core.model

/** Business context in which an inspection is performed. */
enum class InspectionContext {
    PRE_DELIVERY, RENTAL, AUCTION, INSURANCE_CLAIM, SERVICE, RESALE, HANDOVER
}

/** Lifecycle status of an inspection. */
enum class InspectionStatus { DRAFT, IN_PROGRESS, COMPLETED, SYNCED }

/** Whether the inspected vehicle is new or old (used-vehicle). */
enum class VehicleCategory { NEW, OLD }

/** Capture section. DOCUMENT is used for Old-vehicle paperwork photos. */
enum class Section { EXTERIOR, INTERIOR, DOCUMENT }

/** Old-vehicle document types captured as DOCUMENT-section images. */
enum class DocumentType { RC, POLLUTION_CERTIFICATE, INSURANCE }

/** On-device image quality assessment result. */
enum class ImageQuality { OK, BLURRY, DARK, OVEREXPOSED, INCOMPLETE }

/** Capture state of a required position. */
enum class CaptureState { PENDING, CAPTURED, SKIPPED }

/** Severity level for findings and annotations. */
enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

/** Manual annotation shape. */
enum class AnnotationShape { RECTANGLE, FREEHAND, PIN }

/** Origin of an AI finding. */
enum class FindingSource { INITIAL, REVERIFICATION, FINAL }

/** Per-item synchronization state. */
enum class SyncState { PENDING, UPLOADING, SYNCED, FAILED }

/** How a VIN was captured. */
enum class VinInputMethod { MANUAL, OCR, BARCODE }

/** Final repair recommendation for the vehicle (§15 Final Assessment). */
enum class RepairRecommendation(val label: String) {
    NO_REPAIR("No repair needed"),
    COSMETIC_REPAIR("Cosmetic repair"),
    MECHANICAL_SERVICE("Mechanical service"),
    BODY_SHOP_REPAIR("Body shop repair"),
    INSURANCE_CLAIM("Insurance claim"),
    REJECT_VEHICLE("Reject vehicle"),
}

/**
 * Closed taxonomy of damage types spanning exterior and interior findings.
 * OTHER is a safe fallback for validated-but-unmapped categories.
 */
enum class DamageType {
    // Exterior
    DENT, SCRATCH, DEEP_SCRATCH, PAINT_CHIP, PAINT_PEEL, RUST, CRACK,
    BROKEN_PLASTIC, BROKEN_GLASS, BROKEN_HEADLIGHT, BROKEN_TAIL_LIGHT, MIRROR_DAMAGE,
    MISSING_PARTS, LOOSE_PARTS, PANEL_GAP, PANEL_MISALIGNMENT, PAINT_MISMATCH,
    AFTERMARKET_REPAIR_EVIDENCE, CORROSION, WHEEL_DAMAGE, WHEEL_SCRATCH, WHEEL_CRACK,
    TYRE_WEAR, TYRE_BULGE, LOW_TYRE_TREAD, BENT_RIM, OIL_LEAK, FLUID_LEAK,
    BROKEN_BADGE, BROKEN_NUMBER_PLATE, BROKEN_GRILL, BROKEN_BUMPER, FOG_LAMP_DAMAGE,
    STONE_CHIPS, BIRD_DROPPINGS, HEAVY_DIRT,

    // Interior
    SEAT_TEAR, SEAT_STAIN, SEAT_BURN, DASHBOARD_CRACK, DASHBOARD_SCRATCH,
    STEERING_WEAR, STEERING_DAMAGE, BROKEN_BUTTONS, MISSING_BUTTONS, BROKEN_AC_VENT,
    LOOSE_TRIM, BROKEN_DISPLAY, DEAD_PIXELS, BROKEN_SWITCH, BROKEN_MIRROR_CONTROL,
    WATER_DAMAGE, MOLD, EXCESSIVE_DIRT, BAD_ODOR_INDICATORS, FLOOR_DAMAGE,
    ROOF_LINER_DAMAGE, DOOR_PANEL_DAMAGE, MISSING_ACCESSORIES, BROKEN_SEAT_BELT,
    BROKEN_ARM_REST, BROKEN_STORAGE, CRACKED_CONSOLE,

    OTHER
}
