package com.vsp.inspection.feature.common

import com.vsp.core.model.InspectionImage
import com.vsp.core.model.Section
import com.vsp.core.model.catalog.DocumentCatalog
import com.vsp.core.model.catalog.PositionCatalog

/**
 * Restricts a list of images to those that belong to a single checklist section, so every
 * section keeps a unique set. A null [sectionId] returns the list unchanged (whole inspection).
 */
fun List<InspectionImage>.forChecklistSection(sectionId: String?): List<InspectionImage> = when {
    sectionId == null -> this
    sectionId == "documents" -> filter { it.section == Section.DOCUMENT }
    // Standard wizard sections keep their (untagged) coverage photos.
    sectionId == "exterior" -> filter { it.section == Section.EXTERIOR && it.checklistSectionId == null }
    sectionId == "interior" -> filter { it.section == Section.INTERIOR && it.checklistSectionId == null }
    // Every other section owns a set of photos tagged with its id.
    else -> filter { it.checklistSectionId == sectionId }
}

/** Restricts a list of images to those captured for a single checklist item (component). */
fun List<InspectionImage>.forChecklistItem(itemId: String): List<InspectionImage> =
    filter { it.checklistItemId == itemId }

/**
 * Orders images by the guided capture sequence: documents first, then exterior positions,
 * then interior positions (each in [PositionCatalog] order), with capture time as a tiebreaker.
 */
fun List<InspectionImage>.sortedByCaptureSequence(): List<InspectionImage> {
    val exteriorOrder = PositionCatalog.exterior.associate { it.id to it.order }
    val interiorOrder = PositionCatalog.interior.associate { it.id to it.order }
    val documentOrder = DocumentCatalog.oldVehicleDocuments.associate { it.type to it.order }

    fun sectionRank(section: Section): Int = when (section) {
        Section.DOCUMENT -> 0
        Section.EXTERIOR -> 1
        Section.INTERIOR -> 2
    }

    fun positionRank(image: InspectionImage): Int = when (image.section) {
        Section.DOCUMENT -> image.documentType?.let { documentOrder[it] } ?: Int.MAX_VALUE
        Section.EXTERIOR -> exteriorOrder[image.position] ?: Int.MAX_VALUE
        Section.INTERIOR -> interiorOrder[image.position] ?: Int.MAX_VALUE
    }

    return sortedWith(
        compareBy(
            { sectionRank(it.section) },
            { positionRank(it) },
            { it.capturedAt ?: Long.MAX_VALUE },
        ),
    )
}
