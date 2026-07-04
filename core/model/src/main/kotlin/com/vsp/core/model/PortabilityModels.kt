package com.vsp.core.model

/** Result of a successful data export. */
data class ExportResult(
    val filePath: String,
    val inspectionCount: Int,
    val imageCount: Int,
)

/** Non-destructive preview produced by validating an import bundle before applying it. */
data class ImportPreview(
    val inspectionCount: Int,
    val imageCount: Int,
    val questionnaireHash: String,
    val vendorId: String,
)

/** Outcome of applying an import bundle. */
data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val imageCount: Int,
)
