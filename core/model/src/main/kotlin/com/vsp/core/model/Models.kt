package com.vsp.core.model

/** Authenticated inspector. */
data class Inspector(
    val id: String,
    val displayName: String,
    val email: String,
)

/** Active authenticated session. */
data class Session(
    val inspectorId: String,
    val displayName: String,
    val email: String,
    val issuedAtMillis: Long,
)

/** The vehicle under inspection. */
data class Vehicle(
    val id: String,
    val vin: String? = null,
    val category: VehicleCategory = VehicleCategory.NEW,
    val year: Int? = null,
    val manufacturer: String? = null,
    val make: String? = null,
    val model: String? = null,
    val variant: String? = null,
    val trim: String? = null,
    val bodyStyle: String? = null,
    val fuelType: String? = null,
    val transmission: String? = null,
    val color: String? = null,
    val registrationNumber: String? = null,
    val engineNumber: String? = null,
    val chassisNumber: String? = null,
    val numberOfOwnerships: Int? = null,
    val numberOfKeys: Int? = null,
    val odometerKm: Int? = null,
    val vinInputMethod: VinInputMethod = VinInputMethod.MANUAL,
    val decoded: Boolean = false,
)

/** A single inspection session. */
data class Inspection(
    val id: String,
    val inspectorId: String,
    val vehicleId: String,
    val context: InspectionContext,
    val vehicleCategory: VehicleCategory,
    val status: InspectionStatus,
    val currentStep: String,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val deviceInfo: String = "",
    val exteriorScore: Int? = null,
    val interiorScore: Int? = null,
    val safetyScore: Int? = null,
    val cosmeticScore: Int? = null,
    val confidenceScore: Int? = null,
    val overallCondition: String? = null,
    val finalRecommendation: String? = null,
    val summary: String? = null,
    val syncState: SyncState = SyncState.PENDING,
)

/** A captured image at a defined position (exterior/interior) or an Old-vehicle document. */
data class InspectionImage(
    val id: String,
    val inspectionId: String,
    val section: Section,
    val position: String,
    val documentType: DocumentType? = null,
    val checklistSectionId: String? = null,
    val checklistItemId: String? = null,
    val captureState: CaptureState = CaptureState.PENDING,
    val skipReason: String? = null,
    val localFilePath: String = "",
    val thumbnailPath: String? = null,
    val remoteUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
    val capturedAt: Long? = null,
    val orientation: Int? = null,
    val quality: ImageQuality = ImageQuality.OK,
    val aiState: SyncState = SyncState.PENDING,
    val syncState: SyncState = SyncState.PENDING,
)

/** Normalized bounding box (0..1). */
data class BoundingBox(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

/** A validated AI-detected finding. */
data class AIFinding(
    val id: String,
    val imageId: String,
    val damageType: DamageType,
    val confidence: Float,
    val severity: Severity,
    val boundingBox: BoundingBox,
    val repairRecommendation: String,
    val reviewRequired: Boolean,
    val source: FindingSource,
    val createdAt: Long,
)

/** A manual annotation on an image. */
data class Annotation(
    val id: String,
    val imageId: String,
    val shape: AnnotationShape,
    val geometryJson: String,
    val damageType: DamageType,
    val severity: Severity,
    val comment: String? = null,
    val component: String? = null,
    val vehicleSide: String? = null,
    val estimatedSize: String? = null,
    val repairRequired: Boolean? = null,
    val estimatedCost: Double? = null,
    val manualVerified: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Generated inspection report. */
data class Report(
    val id: String,
    val inspectionId: String,
    val json: String,
    val localJsonPath: String? = null,
    val remoteJsonUrl: String? = null,
    val generatedAt: Long,
    val status: String,
    val syncState: SyncState = SyncState.PENDING,
)

/** Audit-log event for an inspection. */
data class AuditLogEntry(
    val id: String,
    val inspectionId: String,
    val eventType: String,
    val actorId: String,
    val timestamp: Long,
    val detailJson: String? = null,
)

/** Completeness assessment used to gate finalization. */
data class Completeness(
    val totalMandatory: Int,
    val addressed: Int,
    val missingPositions: List<String>,
    val isComplete: Boolean,
)

/** Scores produced by final verification. */
data class VerificationScores(
    val exterior: Int,
    val interior: Int,
    val safety: Int,
    val cosmetic: Int,
    val confidence: Int,
)

/** Integrity flags produced by final verification. */
data class IntegrityFlags(
    val missingImages: List<String> = emptyList(),
    val duplicateImages: List<String> = emptyList(),
    val lowQualityImages: List<String> = emptyList(),
    val suspiciousImages: List<String> = emptyList(),
    val potentialFraud: Boolean = false,
)

/** Result of a whole-inspection final verification. */
data class FinalVerification(
    val scores: VerificationScores,
    val overallCondition: String,
    val summary: String,
    val integrity: IntegrityFlags,
)

/** Result of AI re-verifying a manual annotation. */
data class ReverifyResult(
    val confirmed: Boolean,
    val correctedDamageType: DamageType? = null,
    val correctedSeverity: Severity? = null,
    val nearbyFindings: List<AIFinding> = emptyList(),
    val mergedWithFindingIds: List<String> = emptyList(),
    val inconsistency: Boolean = false,
)

/** Aggregated per-inspection sync summary. */
data class SyncSummary(
    val pending: Int,
    val uploading: Int,
    val synced: Int,
    val failed: Int,
) {
    val isFullySynced: Boolean get() = pending == 0 && uploading == 0 && failed == 0
}
