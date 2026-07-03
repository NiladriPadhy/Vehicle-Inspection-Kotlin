package com.vsp.inspection.feature.common

import com.vsp.core.model.InspectionImage
import com.vsp.core.model.Section
import com.vsp.core.model.catalog.ChecklistCatalog
import com.vsp.core.model.catalog.DocumentCatalog
import com.vsp.core.model.catalog.PositionCatalog

/**
 * Human-readable path label for an inspection image. For checklist-tagged photos this is the
 * full "Section -> Group -> Item" path, e.g. "Exterior Inspection -> Front -> Front Bumper".
 * Falls back to "Section -> Position" for legacy wizard/document captures.
 */
fun InspectionImage.typeLabel(): String {
    checklistItemId?.let { itemId ->
        val item = ChecklistCatalog.item(itemId)
        val section = ChecklistCatalog.sectionForItem(itemId)
        if (item != null && section != null) {
            val group = section.groups.firstOrNull { g -> g.items.any { it.id == itemId } }
            val parts = listOfNotNull(
                section.title,
                group?.title?.takeIf { it != item.label && it != section.title },
                item.label,
            )
            return parts.joinToString(" \u2192 ")
        }
    }

    val sectionLabel = when (section) {
        Section.EXTERIOR -> "Exterior"
        Section.INTERIOR -> "Interior"
        Section.DOCUMENT -> "Document"
    }
    val positionLabel = when (section) {
        Section.DOCUMENT -> DocumentCatalog.oldVehicleDocuments
            .firstOrNull { it.type == documentType }?.displayName
            ?: documentType?.name?.replace('_', ' ')
            ?: position.replace('_', ' ')
        else -> PositionCatalog.forSection(section)
            .firstOrNull { it.id == position }?.displayName
            ?: checklistSectionId?.let { ChecklistCatalog.section(it)?.title }
            ?: position.substringBefore('_').replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
    return "$sectionLabel \u2192 $positionLabel"
}
