package com.vsp.core.data.report

import com.vsp.core.model.AIFinding
import com.vsp.core.model.Annotation
import com.vsp.core.model.ChecklistResponse
import com.vsp.core.model.Inspection
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.Inspector
import com.vsp.core.model.Severity
import com.vsp.core.model.ValuationCalculator
import com.vsp.core.model.Vehicle
import com.vsp.core.model.VehicleCategory
import com.vsp.core.model.catalog.Applicability
import com.vsp.core.model.catalog.ChecklistCatalog
import com.vsp.core.model.catalog.ChecklistStatus
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Pure assembler that turns the local inspection graph into the report JSON defined by
 * contracts/report-json-schema.json. Deterministic and unit-tested (see ReportBuilderTest).
 */
class ReportBuilder @Inject constructor() {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    data class ImageBundle(
        val image: InspectionImage,
        val annotations: List<Annotation>,
        val findings: List<AIFinding>,
    )

    fun build(
        reportId: String,
        inspection: Inspection,
        vehicle: Vehicle,
        inspector: Inspector,
        device: ReportDeviceDto,
        images: List<ImageBundle>,
        inspectorNotes: String? = null,
        checklist: List<ChecklistResponse> = emptyList(),
    ): ReportDto {
        val allFindings = images.flatMap { it.findings }
        val allAnnotations = images.flatMap { it.annotations }
        val bySeverity = SeverityCountsDto(
            low = countSeverity(allFindings, allAnnotations, Severity.LOW),
            medium = countSeverity(allFindings, allAnnotations, Severity.MEDIUM),
            high = countSeverity(allFindings, allAnnotations, Severity.HIGH),
            critical = countSeverity(allFindings, allAnnotations, Severity.CRITICAL),
        )
        val finalAssessment = buildFinalAssessment(inspection, checklist)
        // Prefer the inspector's explicit category ratings; otherwise fall back to a pass/fail-derived
        // rating so the valuation still appears (parity with the PDF, which derives from section stats).
        val explicitOverall = finalAssessment?.categoryRatings?.values
            ?.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val valuation = ValuationCalculator.compute(
            overallRating = explicitOverall ?: deriveOverallRating(checklist),
            categoryRatings = finalAssessment?.categoryRatings.orEmpty(),
            damageCount = allFindings.size + allAnnotations.size,
            highSeverityCount = bySeverity.high + bySeverity.critical,
        )
        return ReportDto(
            reportId = reportId,
            inspectionId = inspection.id,
            context = inspection.context.name,
            vehicle = vehicle.toDto(),
            inspector = ReportInspectorDto(inspector.id, inspector.displayName),
            inspectionTime = ReportTimeDto(inspection.createdAt, inspection.completedAt),
            gps = if (inspection.gpsLat != null && inspection.gpsLng != null) {
                ReportGpsDto(inspection.gpsLat!!, inspection.gpsLng!!)
            } else null,
            device = device,
            // Checklist-tagged photos are nested under their checklist item; keep only
            // untagged (legacy wizard/document) captures in the flat top-level list.
            images = images
                .filter { it.image.checklistItemId == null }
                .sortedBy { imageOrder(it.image) }
                .map { it.toDto() },
            damageSummary = DamageSummaryDto(
                totalDamageCount = allFindings.size + allAnnotations.size,
                bySeverity = bySeverity,
            ),
            scores = ReportScoresDto(
                exterior = inspection.exteriorScore ?: 0,
                interior = inspection.interiorScore ?: 0,
                safety = inspection.safetyScore ?: 0,
                cosmetic = inspection.cosmeticScore ?: 0,
                confidence = inspection.confidenceScore ?: 0,
            ),
            integrity = ReportIntegrityDto(
                emptyList(), emptyList(), emptyList(), emptyList(), potentialFraud = false,
            ),
            overallCondition = inspection.overallCondition ?: "UNKNOWN",
            inspectorNotes = inspectorNotes,
            finalRecommendation = inspection.finalRecommendation ?: "REVIEW",
            inspectionStatus = inspection.status.name,
            checklist = buildChecklist(vehicle.category, checklist, images),
            damageAssessment = buildDamageAssessment(images),
            finalAssessment = finalAssessment,
            valuation = valuation,
        )
    }

    private fun applicability(category: VehicleCategory) =
        if (category == VehicleCategory.OLD) Applicability.OLD else Applicability.NEW

    /** Catalog ordering for report images; untagged (legacy) images sort to the end by capture time. */
    private fun imageOrder(image: InspectionImage): Long {
        val rank = image.checklistItemId?.let { ChecklistCatalog.itemOrder[it] } ?: Int.MAX_VALUE
        return rank.toLong() * 1_000_000L + (image.capturedAt ?: 0L) % 1_000_000L
    }

    /** Human label for the checklist item a photo documents, e.g. "Front Bumper". */
    private fun itemLabel(itemId: String?): String? =
        itemId?.let { ChecklistCatalog.item(it)?.label }

    private fun buildChecklist(
        category: VehicleCategory,
        responses: List<ChecklistResponse>,
        images: List<ImageBundle>,
    ): List<ReportChecklistSectionDto> {
        val byItem = responses.associateBy { it.itemId }
        val imagesByItem = images
            .filter { it.image.checklistItemId != null }
            .sortedBy { imageOrder(it.image) }
            .groupBy { it.image.checklistItemId!! }
        if (byItem.isEmpty() && imagesByItem.isEmpty()) return emptyList()

        return ChecklistCatalog.forCategory(applicability(category)).mapNotNull { section ->
            val items = section.allItems.mapNotNull { item ->
                val r = byItem[item.id]?.takeIf { it.isAnswered }
                val itemImages = imagesByItem[item.id].orEmpty()
                if (r == null && itemImages.isEmpty()) return@mapNotNull null
                ReportChecklistItemDto(
                    itemId = item.id,
                    label = item.label,
                    status = r?.status?.name,
                    rating = r?.rating,
                    numericValue = r?.numericValue,
                    unit = item.unit,
                    textValue = r?.textValue,
                    damageTypes = r?.damageTypes.orEmpty().map { it.name },
                    images = itemImages.map { it.toDto() },
                )
            }
            if (items.isEmpty()) null else ReportChecklistSectionDto(section.id, section.title, items)
        }
    }

    private fun buildDamageAssessment(images: List<ImageBundle>): List<ReportDamageAssessmentDto> =
        images.flatMap { bundle ->
            val ai = bundle.findings.map { f ->
                ReportDamageAssessmentDto(
                    imageId = bundle.image.id,
                    section = bundle.image.section.name,
                    position = bundle.image.position,
                    checklistItemId = bundle.image.checklistItemId,
                    checklistItem = itemLabel(bundle.image.checklistItemId),
                    source = f.source.name,
                    damageType = f.damageType.name,
                    severity = f.severity.name,
                    confidence = f.confidence,
                )
            }
            val manual = bundle.annotations.map { a ->
                ReportDamageAssessmentDto(
                    imageId = bundle.image.id,
                    section = bundle.image.section.name,
                    position = bundle.image.position,
                    checklistItemId = bundle.image.checklistItemId,
                    checklistItem = itemLabel(bundle.image.checklistItemId),
                    source = "MANUAL",
                    damageType = a.damageType.name,
                    severity = a.severity.name,
                    component = a.component,
                    vehicleSide = a.vehicleSide,
                    estimatedSize = a.estimatedSize,
                    repairRequired = a.repairRequired,
                    estimatedCost = a.estimatedCost,
                    manualVerified = a.manualVerified,
                )
            }
            ai + manual
        }

    /** Overall 1–5 rating derived from answered pass/fail-style checklist items (null when none). */
    private fun deriveOverallRating(responses: List<ChecklistResponse>): Int? {
        var perfect = 0
        var imperfect = 0
        responses.filter { it.isAnswered }.forEach { r ->
            when (r.status) {
                ChecklistStatus.OK, ChecklistStatus.YES, ChecklistStatus.PASS, ChecklistStatus.GOOD -> perfect++
                ChecklistStatus.NOT_OK, ChecklistStatus.NO, ChecklistStatus.FAIL,
                ChecklistStatus.MINOR_SCRATCHES, ChecklistStatus.MAJOR_SCRATCHES, ChecklistStatus.DAMAGE,
                -> imperfect++
                else -> {}
            }
        }
        val total = perfect + imperfect
        if (total == 0) return null
        return (perfect.toDouble() / total * 5).roundToInt().coerceIn(1, 5)
    }

    private fun buildFinalAssessment(
        inspection: Inspection,
        responses: List<ChecklistResponse>,
    ): ReportFinalAssessmentDto? {
        val finalSection = ChecklistCatalog.section("final_assessment") ?: return null
        val byItem = responses.associateBy { it.itemId }
        val ratings = finalSection.allItems
            .filter { it.responseType == com.vsp.core.model.catalog.ChecklistResponseType.RATING_1_5 }
            .mapNotNull { item -> byItem[item.id]?.rating?.let { item.label to it } }
            .toMap()
        val recommendation = byItem[ChecklistCatalog.RECOMMENDATION_ITEM_ID]?.textValue
            ?: inspection.finalRecommendation
        val remarks = byItem["fa_remarks"]?.textValue
        if (ratings.isEmpty() && recommendation.isNullOrBlank() && remarks.isNullOrBlank() &&
            inspection.overallCondition.isNullOrBlank()
        ) {
            return null
        }
        return ReportFinalAssessmentDto(
            categoryRatings = ratings,
            overallCondition = inspection.overallCondition,
            recommendation = recommendation,
            remarks = remarks,
        )
    }

    fun toJson(dto: ReportDto): String = json.encodeToString(ReportDto.serializer(), dto)

    private fun countSeverity(
        findings: List<AIFinding>,
        annotations: List<Annotation>,
        severity: Severity,
    ): Int = findings.count { it.severity == severity } + annotations.count { it.severity == severity }

    private fun Vehicle.toDto() = ReportVehicleDto(
        vin = vin,
        category = category.name,
        numberOfOwnerships = numberOfOwnerships,
        numberOfKeys = numberOfKeys,
        year = year,
        manufacturer = manufacturer,
        make = make,
        model = model,
        variant = variant,
        trim = trim,
        bodyStyle = bodyStyle,
        fuelType = fuelType,
        transmission = transmission,
        color = color,
        registrationNumber = registrationNumber,
        engineNumber = engineNumber,
        chassisNumber = chassisNumber,
        odometerKm = odometerKm,
    )

    private fun ImageBundle.toDto() = ReportImageDto(
        imageId = image.id,
        section = image.section.name,
        position = image.position,
        checklistSectionId = image.checklistSectionId,
        checklistItemId = image.checklistItemId,
        checklistItem = itemLabel(image.checklistItemId),
        documentType = image.documentType?.name,
        captureState = image.captureState.name,
        skipReason = image.skipReason,
        thumbnailUrl = image.thumbnailPath ?: image.remoteUrl,
        imageUrl = image.remoteUrl ?: image.localFilePath,
        metadata = ReportImageMetadataDto(
            width = image.width,
            height = image.height,
            sizeBytes = image.sizeBytes,
            capturedAt = image.capturedAt,
            orientation = image.orientation,
            quality = image.quality.name,
        ),
        annotations = annotations.map {
            ReportAnnotationDto(it.shape.name, it.geometryJson, it.damageType.name, it.severity.name, it.comment)
        },
        aiFindings = findings.map {
            ReportFindingDto(
                damageType = it.damageType.name,
                confidence = it.confidence,
                severity = it.severity.name,
                boundingBox = ReportBoxDto(it.boundingBox.x, it.boundingBox.y, it.boundingBox.w, it.boundingBox.h),
                repairRecommendation = it.repairRecommendation,
                reviewRequired = it.reviewRequired,
                source = it.source.name,
            )
        },
    )
}
