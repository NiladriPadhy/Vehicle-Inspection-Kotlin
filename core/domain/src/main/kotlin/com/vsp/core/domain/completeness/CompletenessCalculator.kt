package com.vsp.core.domain.completeness

import com.vsp.core.model.CaptureState
import com.vsp.core.model.Completeness
import com.vsp.core.model.DocumentType
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.Section
import com.vsp.core.model.VehicleCategory
import com.vsp.core.model.catalog.PositionCatalog
import javax.inject.Inject

/**
 * Pure logic that determines whether an inspection has addressed every mandatory item:
 * all mandatory exterior/interior positions and, for Old vehicles, the required documents.
 * A position is "addressed" when CAPTURED (quality OK) or SKIPPED with a reason.
 */
class CompletenessCalculator @Inject constructor() {

    fun calculate(
        category: VehicleCategory,
        images: List<InspectionImage>,
    ): Completeness {
        val mandatoryPositions = buildList {
            PositionCatalog.exterior.filter { it.mandatory }.forEach { add(Section.EXTERIOR to it.id) }
            PositionCatalog.interior.filter { it.mandatory }.forEach { add(Section.INTERIOR to it.id) }
            if (category == VehicleCategory.OLD) {
                DocumentType.entries.forEach { add(Section.DOCUMENT to it.name) }
            }
        }

        val addressedKeys = images
            .filter { it.isAddressed() }
            .map { it.section to it.positionKey() }
            .toSet()

        val missing = mandatoryPositions
            .filterNot { it in addressedKeys }
            .map { (section, id) -> "${section.name}:$id" }

        return Completeness(
            totalMandatory = mandatoryPositions.size,
            addressed = mandatoryPositions.size - missing.size,
            missingPositions = missing,
            isComplete = missing.isEmpty(),
        )
    }

    private fun InspectionImage.isAddressed(): Boolean = when (captureState) {
        CaptureState.CAPTURED -> true
        CaptureState.SKIPPED -> !skipReason.isNullOrBlank()
        CaptureState.PENDING -> false
    }

    private fun InspectionImage.positionKey(): String =
        if (section == Section.DOCUMENT) documentType?.name ?: position else position
}
