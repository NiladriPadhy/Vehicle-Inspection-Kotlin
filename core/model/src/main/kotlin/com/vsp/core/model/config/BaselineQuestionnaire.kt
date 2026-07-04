package com.vsp.core.model.config

import com.vsp.core.model.catalog.ChecklistCatalog

/**
 * Builds the baseline [QuestionnaireConfig] from the bundled [ChecklistCatalog]. This is the
 * authoritative offline baseline: it is used (a) as the seed pushed to a brand-new vendor RTDB that
 * has no configuration yet, and (b) as the offline fallback when the vendor RTDB is unreachable.
 *
 * Keeping the baseline derived from [ChecklistCatalog] (rather than a hand-maintained JSON) removes
 * any risk of the two drifting apart. The generated JSON is also emitted to
 * `app/src/main/assets/baseline_questionnaire.json` (see BaselineQuestionnaireAssetTest) to satisfy
 * the "baseline JSON in assets" requirement.
 */
object BaselineQuestionnaire {

    const val DEFAULT_MAX_IMAGES: Int = 10
    const val BASELINE_VERSION: Int = 1

    fun build(): QuestionnaireConfig {
        var sectionOrder = 0
        val sections = ChecklistCatalog.sections
            .sortedBy { it.order }
            .map { section ->
                var groupOrder = 0
                ConfigSection(
                    id = section.id,
                    title = section.title,
                    order = section.order.takeIf { it != 0 } ?: sectionOrder++,
                    appliesTo = section.appliesTo.name,
                    groups = section.groups.map { group ->
                        var itemOrder = 0
                        ConfigGroup(
                            id = group.id,
                            title = group.title,
                            order = groupOrder++,
                            items = group.items.map { item ->
                                ConfigItem(
                                    id = item.id,
                                    label = item.label,
                                    responseType = item.responseType.name,
                                    appliesTo = item.appliesTo.name,
                                    unit = item.unit,
                                    mandatory = item.mandatory,
                                    allowImage = item.photoCapable,
                                    maxImages = if (item.photoCapable) DEFAULT_MAX_IMAGES else 0,
                                    order = itemOrder++,
                                )
                            },
                        )
                    },
                )
            }
        val base = QuestionnaireConfig(version = BASELINE_VERSION, sections = sections)
        return base.copy(hash = ConfigHashing.hash(base))
    }
}
