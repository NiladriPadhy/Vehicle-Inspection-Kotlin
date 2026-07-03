package com.vsp.core.model

import com.vsp.core.model.catalog.ChecklistStatus

/**
 * A saved answer for a single checklist item (see [com.vsp.core.model.catalog.ChecklistCatalog]).
 * The populated field depends on the item's response type.
 */
data class ChecklistResponse(
    val id: String,
    val inspectionId: String,
    val itemId: String,
    val status: ChecklistStatus? = null,
    val rating: Int? = null,
    val numericValue: Double? = null,
    val textValue: String? = null,
    val damageTypes: List<DamageType> = emptyList(),
    val updatedAt: Long,
    val syncState: SyncState = SyncState.PENDING,
) {
    /** Whether this item has any meaningful answer recorded. */
    val isAnswered: Boolean
        get() = status != null || rating != null || numericValue != null ||
            !textValue.isNullOrBlank() || damageTypes.isNotEmpty()
}

/** Per-section completion used to drive the checklist hub badges. */
data class ChecklistSectionProgress(
    val sectionId: String,
    val answered: Int,
    val total: Int,
) {
    val isComplete: Boolean get() = total > 0 && answered >= total
}
