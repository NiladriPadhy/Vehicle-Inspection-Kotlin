package com.vsp.core.domain.usecase

import com.vsp.core.domain.repository.ExportRepository
import com.vsp.core.domain.repository.ImportRepository
import com.vsp.core.model.AppResult
import com.vsp.core.model.ExportResult
import com.vsp.core.model.ImportPreview
import com.vsp.core.model.ImportResult
import javax.inject.Inject

/** Bundles all of the inspector's data into a shareable zip (root → per-inspection → images + CSV). */
class ExportDataUseCase @Inject constructor(
    private val exportRepository: ExportRepository,
) {
    suspend operator fun invoke(inspectorId: String): AppResult<ExportResult> =
        exportRepository.exportAll(inspectorId)
}

/** Validates an import bundle without applying it (structure + questionnaire/CSV compatibility). */
class ValidateImportUseCase @Inject constructor(
    private val importRepository: ImportRepository,
) {
    suspend operator fun invoke(zipPath: String): AppResult<ImportPreview> =
        importRepository.validate(zipPath)
}

/** Validates then applies an import bundle to the local store (device migration). */
class ImportDataUseCase @Inject constructor(
    private val importRepository: ImportRepository,
) {
    suspend operator fun invoke(zipPath: String, inspectorId: String): AppResult<ImportResult> =
        importRepository.import(zipPath, inspectorId)
}
