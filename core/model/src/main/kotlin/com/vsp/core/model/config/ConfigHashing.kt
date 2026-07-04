package com.vsp.core.model.config

import java.security.MessageDigest

/**
 * Deterministic content hashing for questionnaire configurations.
 *
 * The hash is the single source of truth for compatibility checks (active vs snapshot vs imported
 * bundle — see plan §11). It is computed over a canonical string form with sections/groups/items/
 * options sorted by (order, id) so that map ordering from RTDB or JSON never affects the result.
 * The [QuestionnaireConfig.version] and [QuestionnaireConfig.hash] fields themselves are excluded.
 */
object ConfigHashing {

    fun canonicalForm(config: QuestionnaireConfig): String = buildString {
        config.sections.sortedWith(compareBy({ it.order }, { it.id })).forEach { section ->
            append("S:").append(section.id).append('|').append(section.title)
                .append('|').append(section.appliesTo).append('\n')
            section.groups.sortedWith(compareBy({ it.order }, { it.id })).forEach { group ->
                append("  G:").append(group.id).append('|').append(group.title).append('\n')
                group.items.sortedWith(compareBy({ it.order }, { it.id })).forEach { item ->
                    append("    I:").append(item.id)
                        .append('|').append(item.label)
                        .append('|').append(item.responseType)
                        .append('|').append(item.appliesTo)
                        .append('|').append(item.unit ?: "")
                        .append('|').append(item.mandatory)
                        .append('|').append(item.allowImage)
                        .append('|').append(item.maxImages)
                    item.options.sortedWith(compareBy({ it.order }, { it.value })).forEach { opt ->
                        append("|O:").append(opt.value).append('=').append(opt.label)
                    }
                    append('\n')
                }
            }
        }
    }

    fun hash(config: QuestionnaireConfig): String = sha256(canonicalForm(config))

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }
}
