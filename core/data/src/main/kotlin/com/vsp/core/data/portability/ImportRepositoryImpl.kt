package com.vsp.core.data.portability

import android.content.Context
import com.vsp.core.data.io.FileStore
import com.vsp.core.data.local.dao.AnnotationDao
import com.vsp.core.data.local.dao.ChecklistResponseDao
import com.vsp.core.data.local.dao.InspectionDao
import com.vsp.core.data.local.dao.InspectionImageDao
import com.vsp.core.data.local.dao.VehicleDao
import com.vsp.core.data.local.entity.AnnotationEntity
import com.vsp.core.data.local.entity.ChecklistResponseEntity
import com.vsp.core.data.local.entity.InspectionEntity
import com.vsp.core.data.local.entity.InspectionImageEntity
import com.vsp.core.data.local.entity.VehicleEntity
import com.vsp.core.domain.coroutine.DispatcherProvider
import com.vsp.core.domain.repository.ConfigRepository
import com.vsp.core.domain.repository.ImportRepository
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.ImportPreview
import com.vsp.core.model.ImportResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates and applies an export bundle for device migration.
 *
 * Import is hard-blocked (feature 002 §13, D-4) unless:
 *  1. the bundle is structurally valid (manifest + per-inspection folder/CSV/inspection.json),
 *  2. every inspection's pinned questionnaire hash equals the app's current active questionnaire
 *     hash (an admin changing the Firebase questionnaire makes older bundles incompatible), and
 *  3. each data.csv matches the expected schema and references only known questionnaire item ids.
 *
 * Conflict policy (O-3): inspection ids already present locally are skipped (idempotent).
 */
@Singleton
class ImportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: ConfigRepository,
    private val fileStore: FileStore,
    private val inspectionDao: InspectionDao,
    private val vehicleDao: VehicleDao,
    private val imageDao: InspectionImageDao,
    private val responseDao: ChecklistResponseDao,
    private val annotationDao: AnnotationDao,
    private val dispatchers: DispatcherProvider,
) : ImportRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun validate(zipPath: String): AppResult<ImportPreview> = withContext(dispatchers.io) {
        val work = extractDir()
        try {
            validateInto(zipPath, work)
        } finally {
            work.deleteRecursively()
        }
    }

    override suspend fun import(zipPath: String, inspectorId: String): AppResult<ImportResult> = withContext(dispatchers.io) {
        val work = extractDir()
        try {
            val (manifest, root) = when (val prepared = prepare(zipPath, work)) {
                is AppResult.Failure -> return@withContext AppResult.Failure(prepared.error)
                is AppResult.Success -> prepared.value
            }
            when (val v = validateManifest(manifest, root)) {
                is AppResult.Failure -> return@withContext AppResult.Failure(v.error)
                is AppResult.Success -> Unit
            }

            var imported = 0
            var skipped = 0
            var images = 0
            manifest.inspections.forEach { entry ->
                val insDir = File(root, "inspections/${entry.id}")
                val bundle = runCatching {
                    json.decodeFromString<ExportInspection>(File(insDir, "inspection.json").readText())
                }.getOrNull() ?: return@forEach

                if (inspectionDao.getById(bundle.inspection.id) != null) { skipped++; return@forEach }

                vehicleDao.upsert(bundle.vehicle.toEntity())
                inspectionDao.upsert(bundle.inspection.toEntity(inspectorId))

                bundle.images.forEach { img ->
                    val src = File(insDir, "images/${img.fileName}")
                    val storedPath = if (src.exists()) {
                        images++
                        fileStore.copyImageFrom(bundle.inspection.id, img.id, src)
                    } else {
                        ""
                    }
                    imageDao.upsert(img.toEntity(bundle.inspection.id, storedPath))
                }
                bundle.responses.forEach { responseDao.upsert(it.toEntity(bundle.inspection.id)) }
                bundle.annotations.forEach { annotationDao.insert(it.toEntity()) }
                imported++
            }
            AppResult.Success(ImportResult(imported, skipped, images))
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage(t.message ?: "Import failed", t))
        } finally {
            work.deleteRecursively()
        }
    }

    // --- validation -----------------------------------------------------------

    private suspend fun validateInto(zipPath: String, work: File): AppResult<ImportPreview> {
        val (manifest, root) = when (val prepared = prepare(zipPath, work)) {
            is AppResult.Failure -> return AppResult.Failure(prepared.error)
            is AppResult.Success -> prepared.value
        }
        when (val v = validateManifest(manifest, root)) {
            is AppResult.Failure -> return AppResult.Failure(v.error)
            is AppResult.Success -> Unit
        }
        return AppResult.Success(
            ImportPreview(manifest.inspectionCount, manifest.imageCount, manifest.questionnaireHash, manifest.vendorId),
        )
    }

    private fun prepare(zipPath: String, work: File): AppResult<Pair<BundleManifest, File>> {
        val zip = File(zipPath)
        if (!zip.exists()) return AppResult.Failure(AppError.Validation("Import file not found"))
        return runCatching {
            Zipper.unzip(zip, work)
            // Support both a zipped root folder and a flat zip.
            val root = work.listFiles()?.singleOrNull { it.isDirectory && File(it, "manifest.json").exists() } ?: work
            val manifestFile = File(root, "manifest.json")
            if (!manifestFile.exists()) return AppResult.Failure(AppError.Validation("Invalid bundle: manifest.json missing"))
            val manifest = json.decodeFromString<BundleManifest>(manifestFile.readText())
            AppResult.Success(manifest to root)
        }.getOrElse { AppResult.Failure(AppError.Validation("Invalid or corrupt import bundle")) }
    }

    private suspend fun validateManifest(manifest: BundleManifest, root: File): AppResult<Unit> {
        val activeHash = configRepository.activeQuestionnaire().hash
        val activeItemIds = configRepository.activeQuestionnaire().itemIds

        // 1) Questionnaire compatibility (hard block).
        if (manifest.questionnaireHash.isNotBlank() && manifest.questionnaireHash != activeHash) {
            return mismatch(activeHash, manifest.questionnaireHash)
        }
        manifest.inspections.forEach { entry ->
            if (entry.questionnaireHash.isNotBlank() && entry.questionnaireHash != activeHash) {
                return mismatch(activeHash, entry.questionnaireHash)
            }
            // 2) Structural checks.
            val insDir = File(root, "inspections/${entry.id}")
            if (!File(insDir, "inspection.json").exists()) {
                return AppResult.Failure(AppError.Validation("Invalid bundle: inspection ${entry.id} is missing data"))
            }
            val csv = File(insDir, "data.csv")
            if (!csv.exists()) {
                return AppResult.Failure(AppError.Validation("Invalid bundle: inspection ${entry.id} is missing data.csv"))
            }
            // 3) CSV ↔ questionnaire match.
            when (val c = validateCsv(csv, activeItemIds)) {
                is AppResult.Failure -> return c
                is AppResult.Success -> Unit
            }
        }
        return AppResult.Success(Unit)
    }

    private fun validateCsv(csv: File, activeItemIds: Set<String>): AppResult<Unit> {
        val rows = Csv.parse(csv.readText().removePrefix("\uFEFF"))
        if (rows.isEmpty() || rows.first() != Csv.HEADER) {
            return AppResult.Failure(AppError.Validation("Import blocked: capture CSV format does not match"))
        }
        val itemIdIndex = Csv.HEADER.indexOf("item_id")
        val unknown = rows.drop(1)
            .filter { it.getOrNull(0) == "ITEM" }
            .mapNotNull { it.getOrNull(itemIdIndex) }
            .filter { it.isNotBlank() && it !in activeItemIds }
        return if (unknown.isEmpty()) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure(
                AppError.Validation("Import blocked: questionnaire changed — captured items no longer exist (${unknown.first()})"),
            )
        }
    }

    private fun mismatch(expected: String, found: String): AppResult<Nothing> = AppResult.Failure(
        AppError.Validation("Import blocked: questionnaire mismatch. This bundle was captured with a different checklist and cannot be imported."),
    )

    private fun extractDir(): File =
        File(context.cacheDir, "imports/${System.currentTimeMillis()}").apply { deleteRecursively(); mkdirs() }

    // --- DTO → entity ----------------------------------------------------------

    private fun ExportVehicle.toEntity() = VehicleEntity(
        id, vin, category, year, manufacturer, make, model, variant, trim, bodyStyle, fuelType, transmission,
        color, registrationNumber, engineNumber, chassisNumber, numberOfOwnerships, numberOfKeys, odometerKm,
        vinInputMethod, decoded,
    )

    private fun ExportInspectionData.toEntity(inspectorId: String) = InspectionEntity(
        id = id, inspectorId = inspectorId, vehicleId = vehicleId, context = context, vehicleCategory = vehicleCategory,
        status = status, currentStep = currentStep, createdAt = createdAt, updatedAt = updatedAt, completedAt = completedAt,
        gpsLat = gpsLat, gpsLng = gpsLng, deviceInfo = deviceInfo, exteriorScore = exteriorScore, interiorScore = interiorScore,
        safetyScore = safetyScore, cosmeticScore = cosmeticScore, confidenceScore = confidenceScore,
        overallCondition = overallCondition, finalRecommendation = finalRecommendation, summary = summary,
        syncState = "PENDING", checklistVersion = checklistVersion, checklistHash = checklistHash,
        checklistSnapshotJson = checklistSnapshotJson,
    )

    private fun ExportImage.toEntity(inspectionId: String, localPath: String) = InspectionImageEntity(
        id = id, inspectionId = inspectionId, section = section, position = position, documentType = documentType,
        checklistSectionId = checklistSectionId, checklistItemId = checklistItemId, captureState = captureState,
        skipReason = skipReason, localFilePath = localPath, thumbnailPath = null, remoteUrl = null, width = width,
        height = height, sizeBytes = sizeBytes, capturedAt = capturedAt, orientation = orientation, quality = quality,
        aiState = "PENDING", syncState = "PENDING",
    )

    private fun ExportResponse.toEntity(inspectionId: String) = ChecklistResponseEntity(
        id = id, inspectionId = inspectionId, itemId = itemId, status = status, rating = rating,
        numericValue = numericValue, textValue = textValue, damageTypesCsv = damageTypesCsv, updatedAt = updatedAt,
        syncState = "PENDING",
    )

    private fun ExportAnnotation.toEntity() = AnnotationEntity(
        id = id, imageId = imageId, shape = shape, geometryJson = geometryJson, damageType = damageType,
        severity = severity, comment = comment, component = component, vehicleSide = vehicleSide,
        estimatedSize = estimatedSize, repairRequired = repairRequired, estimatedCost = estimatedCost,
        manualVerified = manualVerified, createdAt = createdAt, updatedAt = updatedAt,
    )
}
