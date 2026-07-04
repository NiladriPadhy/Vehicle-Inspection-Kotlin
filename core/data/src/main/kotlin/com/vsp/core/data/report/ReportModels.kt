package com.vsp.core.data.report

import com.vsp.core.model.Valuation
import kotlinx.serialization.Serializable

@Serializable
data class ReportDto(
    val reportId: String,
    val inspectionId: String,
    val context: String?,
    val vehicle: ReportVehicleDto,
    val inspector: ReportInspectorDto,
    val inspectionTime: ReportTimeDto,
    val gps: ReportGpsDto?,
    val device: ReportDeviceDto,
    val images: List<ReportImageDto>,
    val damageSummary: DamageSummaryDto,
    val scores: ReportScoresDto,
    val integrity: ReportIntegrityDto,
    val overallCondition: String,
    val inspectorNotes: String?,
    val finalRecommendation: String,
    val inspectionStatus: String,
    val checklist: List<ReportChecklistSectionDto> = emptyList(),
    val damageAssessment: List<ReportDamageAssessmentDto> = emptyList(),
    val finalAssessment: ReportFinalAssessmentDto? = null,
    val valuation: Valuation? = null,
)

@Serializable
data class ReportVehicleDto(
    val vin: String?,
    val category: String,
    val numberOfOwnerships: Int?,
    val numberOfKeys: Int?,
    val year: Int?,
    val manufacturer: String?,
    val make: String?,
    val model: String?,
    val variant: String?,
    val trim: String?,
    val bodyStyle: String?,
    val fuelType: String?,
    val transmission: String?,
    val color: String?,
    val registrationNumber: String?,
    val engineNumber: String?,
    val chassisNumber: String?,
    val odometerKm: Int? = null,
)

@Serializable
data class ReportInspectorDto(val id: String, val displayName: String)

@Serializable
data class ReportChecklistItemDto(
    val itemId: String,
    val label: String,
    val status: String? = null,
    val rating: Int? = null,
    val numericValue: Double? = null,
    val unit: String? = null,
    val textValue: String? = null,
    val damageTypes: List<String> = emptyList(),
    val images: List<ReportImageDto> = emptyList(),
)

@Serializable
data class ReportChecklistSectionDto(
    val sectionId: String,
    val title: String,
    val items: List<ReportChecklistItemDto>,
)

@Serializable
data class ReportDamageAssessmentDto(
    val imageId: String,
    val section: String,
    val position: String,
    val checklistItemId: String? = null,
    val checklistItem: String? = null,
    val source: String,
    val damageType: String,
    val severity: String,
    val component: String? = null,
    val vehicleSide: String? = null,
    val estimatedSize: String? = null,
    val confidence: Float? = null,
    val repairRequired: Boolean? = null,
    val estimatedCost: Double? = null,
    val manualVerified: Boolean = false,
)

@Serializable
data class ReportFinalAssessmentDto(
    val categoryRatings: Map<String, Int> = emptyMap(),
    val overallCondition: String? = null,
    val recommendation: String? = null,
    val remarks: String? = null,
)

@Serializable
data class ReportTimeDto(val createdAt: Long, val completedAt: Long?)

@Serializable
data class ReportGpsDto(val lat: Double, val lng: Double)

@Serializable
data class ReportDeviceDto(val model: String, val osVersion: String, val appVersion: String)

@Serializable
data class ReportImageDto(
    val imageId: String,
    val section: String,
    val position: String,
    val checklistSectionId: String? = null,
    val checklistItemId: String? = null,
    val checklistItem: String? = null,
    val documentType: String?,
    val captureState: String,
    val skipReason: String?,
    val thumbnailUrl: String?,
    val imageUrl: String?,
    val metadata: ReportImageMetadataDto,
    val annotations: List<ReportAnnotationDto>,
    val aiFindings: List<ReportFindingDto>,
)

@Serializable
data class ReportImageMetadataDto(
    val width: Int?,
    val height: Int?,
    val sizeBytes: Long?,
    val capturedAt: Long?,
    val orientation: Int?,
    val quality: String,
)

@Serializable
data class ReportAnnotationDto(
    val shape: String,
    val geometry: String,
    val damageType: String,
    val severity: String,
    val comment: String?,
)

@Serializable
data class ReportFindingDto(
    val damageType: String,
    val confidence: Float,
    val severity: String,
    val boundingBox: ReportBoxDto,
    val repairRecommendation: String?,
    val reviewRequired: Boolean,
    val source: String,
)

@Serializable
data class ReportBoxDto(val x: Float, val y: Float, val w: Float, val h: Float)

@Serializable
data class DamageSummaryDto(val totalDamageCount: Int, val bySeverity: SeverityCountsDto)

@Serializable
data class SeverityCountsDto(val low: Int, val medium: Int, val high: Int, val critical: Int)

@Serializable
data class ReportScoresDto(
    val exterior: Int,
    val interior: Int,
    val safety: Int,
    val cosmetic: Int,
    val confidence: Int,
)

@Serializable
data class ReportIntegrityDto(
    val missingImages: List<String>,
    val duplicateImages: List<String>,
    val lowQualityImages: List<String>,
    val suspiciousImages: List<String>,
    val potentialFraud: Boolean,
)
