package com.vsp.core.data.portability

import android.content.Context
import com.vsp.core.data.local.dao.AnnotationDao
import com.vsp.core.data.local.dao.ChecklistResponseDao
import com.vsp.core.data.local.dao.InspectionDao
import com.vsp.core.data.local.dao.InspectionImageDao
import com.vsp.core.data.local.dao.VehicleDao
import com.vsp.core.data.local.entity.InspectionEntity
import com.vsp.core.data.local.entity.InspectionImageEntity
import com.vsp.core.data.local.entity.VehicleEntity
import com.vsp.core.domain.coroutine.DispatcherProvider
import com.vsp.core.domain.repository.ExportRepository
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.ExportResult
import com.vsp.core.model.config.QuestionnaireConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bundles every inspection owned by the inspector into a shareable zip:
 *
 *   root/manifest.json
 *   root/questionnaires/{hash}.json
 *   root/inspections/{id}/inspection.json     (lossless import mirror)
 *   root/inspections/{id}/data.csv            (human-readable + CSV-match validation)
 *   root/inspections/{id}/images/{imageId}.jpg
 *
 * The zip is written to the app cache dir; the UI shares it via FileProvider.
 */
@Singleton
class ExportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inspectionDao: InspectionDao,
    private val vehicleDao: VehicleDao,
    private val imageDao: InspectionImageDao,
    private val responseDao: ChecklistResponseDao,
    private val annotationDao: AnnotationDao,
    private val dispatchers: DispatcherProvider,
) : ExportRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    override suspend fun exportAll(inspectorId: String): AppResult<ExportResult> = withContext(dispatchers.io) {
        runCatching {
            val inspections = inspectionDao.getAll().filter { it.inspectorId == inspectorId }
            if (inspections.isEmpty()) {
                return@withContext AppResult.Failure(AppError.Validation("No inspections to export"))
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val root = File(context.cacheDir, "exports/VSP_Export_$stamp").apply { deleteRecursively(); mkdirs() }
            val questionnairesDir = File(root, "questionnaires").apply { mkdirs() }

            var totalImages = 0
            val manifestEntries = mutableListOf<ManifestEntry>()
            val writtenQuestionnaires = mutableSetOf<String>()

            inspections.forEach { inspection ->
                val insDir = File(root, "inspections/${inspection.id}").apply { mkdirs() }
                val imagesDir = File(insDir, "images").apply { mkdirs() }
                val vehicle = vehicleDao.getById(inspection.vehicleId)
                val images = imageDao.getForInspection(inspection.id)
                val responses = responseDao.getForInspection(inspection.id)
                val annotations = images.flatMap { annotationDao.getForImage(it.id) }

                // Copy image files (plaintext JPEG on disk) into the bundle.
                images.forEach { img ->
                    val src = File(img.localFilePath)
                    if (src.exists()) {
                        src.copyTo(File(imagesDir, "${img.id}.jpg"), overwrite = true)
                        totalImages++
                    }
                }

                // Lossless machine-readable mirror.
                val bundle = ExportInspection(
                    inspection = inspection.toExportData(),
                    vehicle = vehicle.toExport(inspection.vehicleId),
                    images = images.map { it.toExport() },
                    responses = responses.map {
                        ExportResponse(it.id, it.itemId, it.status, it.rating, it.numericValue, it.textValue, it.damageTypesCsv, it.updatedAt)
                    },
                    annotations = annotations.map {
                        ExportAnnotation(
                            it.id, it.imageId, it.shape, it.geometryJson, it.damageType, it.severity, it.comment,
                            it.component, it.vehicleSide, it.estimatedSize, it.repairRequired, it.estimatedCost,
                            it.manualVerified, it.createdAt, it.updatedAt,
                        )
                    },
                )
                File(insDir, "inspection.json").writeText(json.encodeToString(bundle))

                // Human-readable CSV (also used for CSV-match validation on import).
                val snapshot = inspection.checklistSnapshotJson?.let {
                    runCatching { json.decodeFromString<QuestionnaireConfig>(it) }.getOrNull()
                }
                File(insDir, "data.csv").writeText(buildCsv(inspection, vehicle, images, responses, annotations, snapshot))

                // One questionnaire snapshot per distinct hash.
                val hash = inspection.checklistHash.orEmpty()
                if (hash.isNotBlank() && writtenQuestionnaires.add(hash) && inspection.checklistSnapshotJson != null) {
                    File(questionnairesDir, "${safeFile(hash)}.json").writeText(inspection.checklistSnapshotJson!!)
                }

                manifestEntries += ManifestEntry(inspection.id, hash, images.size)
            }

            val first = inspections.first()
            val manifest = BundleManifest(
                appVersion = APP_VERSION,
                vendorId = BuildConfigVendor.vendorId,
                exportedAt = System.currentTimeMillis(),
                inspectorUid = inspectorId,
                inspectorEmail = "",
                questionnaireHash = first.checklistHash.orEmpty(),
                questionnaireVersion = first.checklistVersion ?: 0,
                inspectionCount = inspections.size,
                imageCount = totalImages,
                inspections = manifestEntries,
            )
            File(root, "manifest.json").writeText(json.encodeToString(manifest))

            val outFile = File(context.cacheDir, "exports/VSP_Export_$stamp.zip").apply { delete() }
            Zipper.zipDir(root, outFile)
            root.deleteRecursively()

            AppResult.Success(ExportResult(outFile.absolutePath, inspections.size, totalImages))
        }.getOrElse {
            AppResult.Failure(AppError.Storage(it.message ?: "Export failed", it))
        }
    }

    private fun buildCsv(
        inspection: InspectionEntity,
        vehicle: VehicleEntity?,
        images: List<InspectionImageEntity>,
        responses: List<com.vsp.core.data.local.entity.ChecklistResponseEntity>,
        annotations: List<com.vsp.core.data.local.entity.AnnotationEntity>,
        snapshot: QuestionnaireConfig?,
    ): String {
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM for spreadsheet friendliness
        sb.append(Csv.row(Csv.HEADER)).append('\n')

        fun meta(key: String, value: String?) {
            if (value.isNullOrBlank()) return
            sb.append(
                Csv.row(listOf("META", inspection.id, "", "", "", "", key, "", "", "", "", "", "", value, "", "", inspection.updatedAt.toString())),
            ).append('\n')
        }
        meta("vin", vehicle?.vin)
        meta("category", inspection.vehicleCategory)
        meta("make", vehicle?.make ?: vehicle?.manufacturer)
        meta("model", vehicle?.model)
        meta("variant", vehicle?.variant)
        meta("year", vehicle?.year?.toString())
        meta("color", vehicle?.color)
        meta("registrationNumber", vehicle?.registrationNumber)
        meta("odometerKm", vehicle?.odometerKm?.toString())
        meta("context", inspection.context)
        meta("status", inspection.status)
        meta("overallCondition", inspection.overallCondition)
        meta("finalRecommendation", inspection.finalRecommendation)

        // Lookup for item → section/group labels from the pinned snapshot.
        data class Loc(val sectionId: String, val sectionTitle: String, val groupId: String, val groupTitle: String, val label: String, val type: String)
        val locations = HashMap<String, Loc>()
        snapshot?.sections?.forEach { s ->
            s.groups.forEach { g ->
                g.items.forEach { it2 -> locations[it2.id] = Loc(s.id, s.title, g.id, g.title, it2.label, it2.responseType) }
            }
        }
        val imagesByItem = images.filter { it.checklistItemId != null }.groupBy { it.checklistItemId }

        responses.forEach { r ->
            val loc = locations[r.itemId]
            val files = imagesByItem[r.itemId]?.joinToString(";") { "${it.id}.jpg" }.orEmpty()
            sb.append(
                Csv.row(
                    listOf(
                        "ITEM", inspection.id, loc?.sectionId ?: "", loc?.sectionTitle ?: "", loc?.groupId ?: "", loc?.groupTitle ?: "",
                        r.itemId, loc?.label ?: "", loc?.type ?: "", r.status ?: "", r.rating?.toString() ?: "",
                        r.numericValue?.toString() ?: "", "", r.textValue ?: "", r.damageTypesCsv ?: "", files, r.updatedAt.toString(),
                    ),
                ),
            ).append('\n')
        }

        annotations.forEach { a ->
            sb.append(
                Csv.row(
                    listOf(
                        "ANNOTATION", inspection.id, a.imageId, "", "", "", a.id, a.component ?: "", a.shape, a.severity, "", "", "",
                        a.comment ?: "", a.damageType, "${a.imageId}.jpg", a.updatedAt.toString(),
                    ),
                ),
            ).append('\n')
        }
        return sb.toString()
    }

    private fun InspectionEntity.toExportData() = ExportInspectionData(
        id, vehicleId, context, vehicleCategory, status, currentStep, createdAt, updatedAt, completedAt,
        gpsLat, gpsLng, deviceInfo, exteriorScore, interiorScore, safetyScore, cosmeticScore, confidenceScore,
        overallCondition, finalRecommendation, summary, checklistVersion, checklistHash, checklistSnapshotJson,
    )

    private fun VehicleEntity?.toExport(vehicleId: String) = ExportVehicle(
        id = this?.id ?: vehicleId,
        vin = this?.vin, category = this?.category ?: "NEW", year = this?.year, manufacturer = this?.manufacturer,
        make = this?.make, model = this?.model, variant = this?.variant, trim = this?.trim, bodyStyle = this?.bodyStyle,
        fuelType = this?.fuelType, transmission = this?.transmission, color = this?.color,
        registrationNumber = this?.registrationNumber, engineNumber = this?.engineNumber, chassisNumber = this?.chassisNumber,
        numberOfOwnerships = this?.numberOfOwnerships, numberOfKeys = this?.numberOfKeys, odometerKm = this?.odometerKm,
        vinInputMethod = this?.vinInputMethod ?: "MANUAL", decoded = this?.decoded ?: false,
    )

    private fun InspectionImageEntity.toExport() = ExportImage(
        id, section, position, documentType, checklistSectionId, checklistItemId, captureState, skipReason,
        "$id.jpg", width, height, sizeBytes, capturedAt, orientation, quality,
    )

    private fun safeFile(hash: String) = hash.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        private const val APP_VERSION = "0.2.0"
    }
}

/** Small indirection so the data module can read the vendor id without depending on app BuildConfig. */
object BuildConfigVendor {
    @Volatile var vendorId: String = "default"
}
