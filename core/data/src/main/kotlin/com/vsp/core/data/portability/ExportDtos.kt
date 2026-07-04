package com.vsp.core.data.portability

import kotlinx.serialization.Serializable

/**
 * Serializable DTOs for the export/import zip bundle. Each inspection folder carries a
 * machine-readable `inspection.json` (lossless reconstruction on import) alongside the
 * human-readable `data.csv` (which is also used for the questionnaire/CSV compatibility check).
 */
@Serializable
data class BundleManifest(
    val appVersion: String,
    val vendorId: String,
    val exportedAt: Long,
    val inspectorUid: String,
    val inspectorEmail: String,
    val questionnaireHash: String,
    val questionnaireVersion: Int,
    val inspectionCount: Int,
    val imageCount: Int,
    val inspections: List<ManifestEntry>,
)

@Serializable
data class ManifestEntry(
    val id: String,
    val questionnaireHash: String,
    val imageCount: Int,
)

@Serializable
data class ExportInspection(
    val inspection: ExportInspectionData,
    val vehicle: ExportVehicle,
    val images: List<ExportImage>,
    val responses: List<ExportResponse>,
    val annotations: List<ExportAnnotation>,
)

@Serializable
data class ExportInspectionData(
    val id: String,
    val vehicleId: String,
    val context: String,
    val vehicleCategory: String,
    val status: String,
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
    val checklistVersion: Int? = null,
    val checklistHash: String? = null,
    val checklistSnapshotJson: String? = null,
)

@Serializable
data class ExportVehicle(
    val id: String,
    val vin: String? = null,
    val category: String,
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
    val vinInputMethod: String = "MANUAL",
    val decoded: Boolean = false,
)

@Serializable
data class ExportImage(
    val id: String,
    val section: String,
    val position: String,
    val documentType: String? = null,
    val checklistSectionId: String? = null,
    val checklistItemId: String? = null,
    val captureState: String,
    val skipReason: String? = null,
    val fileName: String,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
    val capturedAt: Long? = null,
    val orientation: Int? = null,
    val quality: String = "OK",
)

@Serializable
data class ExportResponse(
    val id: String,
    val itemId: String,
    val status: String? = null,
    val rating: Int? = null,
    val numericValue: Double? = null,
    val textValue: String? = null,
    val damageTypesCsv: String? = null,
    val updatedAt: Long,
)

@Serializable
data class ExportAnnotation(
    val id: String,
    val imageId: String,
    val shape: String,
    val geometryJson: String,
    val damageType: String,
    val severity: String,
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
