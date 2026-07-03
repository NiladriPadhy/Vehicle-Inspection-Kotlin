package com.vsp.core.data.repository

import android.os.Build
import com.vsp.core.data.io.FileStore
import com.vsp.core.data.local.dao.AiFindingDao
import com.vsp.core.data.local.dao.AnnotationDao
import com.vsp.core.data.local.dao.ChecklistResponseDao
import com.vsp.core.data.local.dao.InspectionDao
import com.vsp.core.data.local.dao.InspectionImageDao
import com.vsp.core.data.local.dao.InspectorDao
import com.vsp.core.data.local.dao.ReportDao
import com.vsp.core.data.local.dao.VehicleDao
import com.vsp.core.data.mapper.toDomain
import com.vsp.core.data.mapper.toEntity
import com.vsp.core.data.report.HtmlReportGenerator
import com.vsp.core.data.report.ReportBuilder
import com.vsp.core.data.report.ReportDeviceDto
import com.vsp.core.data.report.WebViewPdfPrinter
import com.vsp.core.data.sync.SyncScheduler
import com.vsp.core.domain.coroutine.DispatcherProvider
import com.vsp.core.domain.repository.ReportRepository
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.Inspector
import com.vsp.core.model.Report
import com.vsp.core.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val reportDao: ReportDao,
    private val inspectionDao: InspectionDao,
    private val vehicleDao: VehicleDao,
    private val inspectorDao: InspectorDao,
    private val imageDao: InspectionImageDao,
    private val annotationDao: AnnotationDao,
    private val aiFindingDao: AiFindingDao,
    private val checklistResponseDao: ChecklistResponseDao,
    private val fileStore: FileStore,
    private val reportBuilder: ReportBuilder,
    private val htmlReportGenerator: HtmlReportGenerator,
    private val webViewPdfPrinter: WebViewPdfPrinter,
    private val syncScheduler: SyncScheduler,
    private val dispatchers: DispatcherProvider,
) : ReportRepository {

    override fun observeReport(inspectionId: String): Flow<Report?> =
        reportDao.observe(inspectionId).map { it?.toDomain() }

    override suspend fun generate(inspectionId: String): AppResult<Report> =
        withContext(dispatchers.io) {
            val inspection = inspectionDao.getById(inspectionId)?.toDomain()
                ?: return@withContext AppResult.Failure(AppError.Validation("Inspection not found"))
            val vehicle = vehicleDao.getById(inspection.vehicleId)?.toDomain()
                ?: return@withContext AppResult.Failure(AppError.Validation("Vehicle not found"))
            val inspector = inspectorDao.getById(inspection.inspectorId)?.toDomain()
                ?: Inspector(inspection.inspectorId, "Inspector", "")

            val bundles = imageDao.getForInspection(inspectionId).map { entity ->
                val image = entity.toDomain()
                ReportBuilder.ImageBundle(
                    image = image,
                    annotations = annotationDao.getForImage(image.id).map { it.toDomain() },
                    findings = aiFindingDao.getForImage(image.id).map { it.toDomain() },
                )
            }

            val checklist = checklistResponseDao.getForInspection(inspectionId).map { it.toDomain() }
            val reportId = UUID.randomUUID().toString()
            val dto = reportBuilder.build(
                reportId = reportId,
                inspection = inspection,
                vehicle = vehicle,
                inspector = inspector,
                device = ReportDeviceDto(
                    model = "${Build.MANUFACTURER} ${Build.MODEL}",
                    osVersion = "Android ${Build.VERSION.RELEASE}",
                    appVersion = APP_VERSION,
                ),
                images = bundles,
                checklist = checklist,
            )
            val jsonString = reportBuilder.toJson(dto)
            val path = runCatching { fileStore.writeEncryptedReport(inspectionId, jsonString) }.getOrNull()

            val report = Report(
                id = reportId,
                inspectionId = inspectionId,
                json = jsonString,
                localJsonPath = path,
                generatedAt = System.currentTimeMillis(),
                status = "GENERATED",
                syncState = SyncState.PENDING,
            )
            reportDao.upsert(report.toEntity())
            syncScheduler.requestSync()
            AppResult.Success(report)
        }

    override suspend fun share(inspectionId: String): AppResult<Unit> = withContext(dispatchers.io) {
        val report = reportDao.getForInspection(inspectionId)
            ?: return@withContext AppResult.Failure(AppError.Validation("Generate the report first"))
        // Exporting/sharing is handled by the presentation layer via the stored JSON payload.
        AppResult.Success(Unit)
    }

    override suspend fun exportPdf(inspectionId: String): AppResult<String> = withContext(dispatchers.io) {
        val inspection = inspectionDao.getById(inspectionId)?.toDomain()
            ?: return@withContext AppResult.Failure(AppError.Validation("Inspection not found"))
        val vehicle = vehicleDao.getById(inspection.vehicleId)?.toDomain()
            ?: return@withContext AppResult.Failure(AppError.Validation("Vehicle not found"))
        val inspector = inspectorDao.getById(inspection.inspectorId)?.toDomain()
            ?: Inspector(inspection.inspectorId, "Inspector", "")

        val bundles = imageDao.getForInspection(inspectionId).map { entity ->
            val image = entity.toDomain()
            HtmlReportGenerator.ImageBundle(
                image = image,
                annotations = annotationDao.getForImage(image.id).map { it.toDomain() },
                findings = aiFindingDao.getForImage(image.id).map { it.toDomain() },
            )
        }

        val checklist = checklistResponseDao.getForInspection(inspectionId).map { it.toDomain() }
        val existing = reportDao.getForInspection(inspectionId)
        runCatching {
            // Heavy work (image decode + base64 embed) stays on IO; the WebView print pass hops
            // to the main thread internally.
            val html = htmlReportGenerator.buildHtml(
                inspection = inspection,
                vehicle = vehicle,
                inspector = inspector,
                bundles = bundles,
                generatedAt = existing?.generatedAt ?: System.currentTimeMillis(),
                checklist = checklist,
            )
            val out = webViewPdfPrinter.outputFile("inspection-report-${inspection.id}.pdf")
            webViewPdfPrinter.render(html, out).absolutePath
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(AppError.Storage("Failed to create PDF: ${it.message}")) },
        )
    }

    companion object {
        private const val APP_VERSION = "0.1.0"
    }
}
