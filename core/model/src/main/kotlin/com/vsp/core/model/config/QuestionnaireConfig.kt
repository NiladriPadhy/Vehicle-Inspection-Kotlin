package com.vsp.core.model.config

import kotlinx.serialization.Serializable

/**
 * Vendor-configurable inspection questionnaire fetched from Firebase RTDB (or seeded from the
 * bundled baseline). This is the serializable, wire/persistence form of the checklist: it is stored
 * verbatim inside each inspection as a snapshot so that later Firebase edits never mutate an
 * in-flight inspection (see feature 002 plan §9, §11).
 *
 * Enum-like fields ([ConfigItem.responseType], [ConfigItem.appliesTo]) are kept as Strings so the
 * config remains forward-compatible with server-side additions; helpers map them to the domain
 * catalog enums where needed.
 */
@Serializable
data class QuestionnaireConfig(
    val version: Int,
    val hash: String = "",
    val updatedAt: Long = 0L,
    val sections: List<ConfigSection> = emptyList(),
) {
    /** All item ids across every section/group — used for import validation and completeness. */
    val itemIds: Set<String>
        get() = sections.asSequence()
            .flatMap { it.groups.asSequence() }
            .flatMap { it.items.asSequence() }
            .map { it.id }
            .toSet()

    fun item(id: String): ConfigItem? = sections.asSequence()
        .flatMap { it.groups.asSequence() }
        .flatMap { it.items.asSequence() }
        .firstOrNull { it.id == id }
}

@Serializable
data class ConfigSection(
    val id: String,
    val title: String,
    val order: Int = 0,
    val appliesTo: String = "BOTH",
    val groups: List<ConfigGroup> = emptyList(),
)

@Serializable
data class ConfigGroup(
    val id: String,
    val title: String,
    val order: Int = 0,
    val items: List<ConfigItem> = emptyList(),
)

@Serializable
data class ConfigItem(
    val id: String,
    val label: String,
    val responseType: String,
    val appliesTo: String = "BOTH",
    val unit: String? = null,
    val mandatory: Boolean = false,
    /** Whether photo evidence may be attached to this item (per-question configurable). */
    val allowImage: Boolean = false,
    /** Maximum number of photos allowed for this item; 0 when [allowImage] is false. */
    val maxImages: Int = 0,
    val order: Int = 0,
    /** Optional configurable answer options for choice-style questions. */
    val options: List<ConfigOption> = emptyList(),
)

@Serializable
data class ConfigOption(
    val value: String,
    val label: String,
    val order: Int = 0,
)
