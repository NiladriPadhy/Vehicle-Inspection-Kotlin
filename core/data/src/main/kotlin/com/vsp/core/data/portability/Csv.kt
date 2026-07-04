package com.vsp.core.data.portability

/** Minimal RFC-4180 CSV helpers (quote fields containing quotes, commas, or newlines). */
object Csv {

    val HEADER = listOf(
        "record_type", "inspection_id", "section_id", "section_title", "group_id", "group_title",
        "item_id", "item_label", "response_type", "status", "rating", "numeric_value", "unit",
        "text_value", "damage_types", "image_files", "updated_at",
    )

    fun row(values: List<String?>): String = values.joinToString(",") { escape(it.orEmpty()) }

    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** Parses CSV content into rows of fields, honouring quoted fields and escaped quotes. */
    fun parse(content: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        val text = content.replace("\r\n", "\n").replace('\r', '\n')
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(field.toString()); field.setLength(0) }
                c == '\n' -> { row.add(field.toString()); field.setLength(0); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
