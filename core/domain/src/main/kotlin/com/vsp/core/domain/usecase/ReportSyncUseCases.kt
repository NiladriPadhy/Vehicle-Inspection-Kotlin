package com.vsp.core.domain.usecase

import com.vsp.core.domain.repository.ReportRepository
import com.vsp.core.domain.repository.SyncRepository
import com.vsp.core.model.AppResult
import com.vsp.core.model.Report
import com.vsp.core.model.SyncSummary
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GenerateReportUseCase @Inject constructor(
    private val reportRepository: ReportRepository,
) {
    suspend operator fun invoke(inspectionId: String): AppResult<Report> =
        reportRepository.generate(inspectionId)
}

class ObserveReportUseCase @Inject constructor(
    private val reportRepository: ReportRepository,
) {
    operator fun invoke(inspectionId: String): Flow<Report?> =
        reportRepository.observeReport(inspectionId)
}

class ShareReportUseCase @Inject constructor(
    private val reportRepository: ReportRepository,
) {
    suspend operator fun invoke(inspectionId: String): AppResult<Unit> =
        reportRepository.share(inspectionId)
}

class ExportReportPdfUseCase @Inject constructor(
    private val reportRepository: ReportRepository,
) {
    suspend operator fun invoke(inspectionId: String): AppResult<String> =
        reportRepository.exportPdf(inspectionId)
}

class ObserveSyncStatusUseCase @Inject constructor(
    private val syncRepository: SyncRepository,
) {
    operator fun invoke(inspectionId: String): Flow<SyncSummary> =
        syncRepository.observeSyncStatus(inspectionId)
}

class RetryFailedSyncUseCase @Inject constructor(
    private val syncRepository: SyncRepository,
) {
    suspend operator fun invoke(inspectionId: String): AppResult<Unit> =
        syncRepository.retryFailed(inspectionId)
}
