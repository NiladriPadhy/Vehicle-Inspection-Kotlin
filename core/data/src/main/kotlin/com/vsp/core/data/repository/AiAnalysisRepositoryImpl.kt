package com.vsp.core.data.repository

import android.util.Log
import com.vsp.core.data.ai.AiResponseValidator
import com.vsp.core.data.local.dao.AiFindingDao
import com.vsp.core.data.local.dao.InspectionDao
import com.vsp.core.data.local.dao.InspectionImageDao
import com.vsp.core.data.mapper.toDomain
import com.vsp.core.data.mapper.toEntity
import com.vsp.core.domain.coroutine.DispatcherProvider
import com.vsp.core.domain.port.AiPrompt
import com.vsp.core.domain.port.AiVisionPort
import com.vsp.core.domain.repository.AiAnalysisRepository
import com.vsp.core.model.AIFinding
import com.vsp.core.model.Annotation
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.FinalVerification
import com.vsp.core.model.FindingSource
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.InspectionStatus
import com.vsp.core.model.ReverifyResult
import com.vsp.core.model.SyncState
import com.vsp.core.model.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiAnalysisRepositoryImpl @Inject constructor(
    private val aiVisionPort: AiVisionPort,
    private val validator: AiResponseValidator,
    private val aiFindingDao: AiFindingDao,
    private val imageDao: InspectionImageDao,
    private val inspectionDao: InspectionDao,
    private val dispatchers: DispatcherProvider,
) : AiAnalysisRepository {

    override fun observeFindings(imageId: String): Flow<List<AIFinding>> =
        aiFindingDao.observeForImage(imageId).map { list -> list.map { it.toDomain() } }

    override suspend fun analyzeImage(image: InspectionImage): AppResult<List<AIFinding>> =
        withContext(dispatchers.io) {
            Log.i(TAG, "analyzeImage → id=${image.id}, section=${image.section}, position=${image.position}")
            val bytes = readBytes(image.localFilePath)
            if (bytes == null) {
                Log.e(TAG, "analyzeImage aborted: image file missing at ${image.localFilePath}")
                return@withContext AppResult.Failure(AppError.Storage("Image file missing"))
            }
            val raw = runCatching {
                aiVisionPort.detect(
                    bytes,
                    AiPrompt(image.section.name, image.position, DETECTION_INSTRUCTION),
                )
            }.getOrElse {
                Log.e(TAG, "analyzeImage failed for id=${image.id}", it)
                return@withContext AppResult.Failure(AppError.AiUnavailable(it))
            }

            val result = validator.validateDetection(image.id, raw.rawJson, System.currentTimeMillis())
            result.map { findings ->
                Log.i(TAG, "analyzeImage done → id=${image.id}, findings=${findings.size}")
                aiFindingDao.deleteForImageBySource(image.id, FindingSource.INITIAL.name)
                aiFindingDao.insertAll(findings.map { it.toEntity() })
                imageDao.upsert(image.copy(aiState = SyncState.SYNCED).toEntity())
                findings
            }
        }

    override suspend fun reverifyAnnotation(
        image: InspectionImage,
        annotation: Annotation,
    ): AppResult<ReverifyResult> = withContext(dispatchers.io) {
        val bytes = readBytes(image.localFilePath)
            ?: return@withContext AppResult.Failure(AppError.Storage("Image file missing"))
        val raw = runCatching {
            aiVisionPort.reverify(
                bytes,
                AiPrompt(image.section.name, image.position, reverifyInstruction(annotation)),
            )
        }.getOrElse { return@withContext AppResult.Failure(AppError.AiUnavailable(it)) }
        validator.validateReverify(raw.rawJson)
    }

    override suspend fun runFinalVerification(inspectionId: String): AppResult<FinalVerification> =
        withContext(dispatchers.io) {
            val images = imageDao.getForInspection(inspectionId)
            Log.i(TAG, "runFinalVerification → inspection=$inspectionId, images=${images.size}")
            val raw = runCatching {
                aiVisionPort.finalVerify(
                    AiPrompt("INSPECTION", inspectionId, FINAL_INSTRUCTION),
                    images.mapNotNull { it.remoteUrl ?: it.localFilePath.takeIf(String::isNotBlank) },
                )
            }.getOrElse {
                Log.e(TAG, "runFinalVerification failed for inspection=$inspectionId", it)
                return@withContext AppResult.Failure(AppError.AiUnavailable(it))
            }

            validator.validateFinal(raw.rawJson).map { verification ->
                val inspection = inspectionDao.getById(inspectionId)?.toDomain()
                if (inspection != null) {
                    inspectionDao.upsert(
                        inspection.copy(
                            exteriorScore = verification.scores.exterior,
                            interiorScore = verification.scores.interior,
                            safetyScore = verification.scores.safety,
                            cosmeticScore = verification.scores.cosmetic,
                            confidenceScore = verification.scores.confidence,
                            overallCondition = verification.overallCondition,
                            summary = verification.summary,
                            finalRecommendation = recommendationFor(verification.scores.confidence),
                            status = InspectionStatus.COMPLETED,
                            updatedAt = System.currentTimeMillis(),
                        ).toEntity(),
                    )
                }
                verification
            }
        }

    private fun readBytes(path: String): ByteArray? =
        runCatching { File(path).takeIf { it.exists() }?.readBytes() }.getOrNull()

    private fun reverifyInstruction(annotation: Annotation): String =
        "Re-verify manual annotation of ${annotation.damageType} (${annotation.severity}). $REVERIFY_INSTRUCTION"

    private fun recommendationFor(confidence: Int): String = when {
        confidence >= 80 -> "PASS"
        confidence >= 50 -> "REVIEW"
        else -> "FAIL"
    }

    companion object {
        private const val TAG = "AiAnalysisRepo"
        private const val DETECTION_INSTRUCTION =
            "You are a vehicle damage inspector. Examine this photo of a vehicle and detect visible " +
                "exterior/interior damage, paying special attention to DENTS and SCRATCHES (also " +
                "DEEP_SCRATCH, PAINT_CHIP, CRACK, RUST, BROKEN_GLASS, WHEEL_DAMAGE). " +
                "Return ONLY strict JSON, no prose, in this exact shape: " +
                "{\"findings\":[{\"damageType\":\"DENT|SCRATCH|DEEP_SCRATCH|PAINT_CHIP|CRACK|RUST|OTHER\"," +
                "\"confidence\":0.0-1.0,\"severity\":\"LOW|MEDIUM|HIGH|CRITICAL\"," +
                "\"boundingBox\":{\"x\":0.0-1.0,\"y\":0.0-1.0,\"w\":0.0-1.0,\"h\":0.0-1.0}," +
                "\"repairRecommendation\":\"short text\"}]}. " +
                "Coordinates are normalized (0..1) from the top-left, where x,y is the box origin and " +
                "w,h its size. If there is no visible damage return {\"findings\":[]}."
        private const val REVERIFY_INSTRUCTION =
            "Return JSON: {\"confirmed\":bool,\"correctedDamageType\",\"correctedSeverity\",\"inconsistency\":bool}."
        private const val FINAL_INSTRUCTION =
            "You are auditing a completed vehicle inspection using the attached photos. Assess overall " +
                "vehicle condition and inspection integrity. Return ONLY strict JSON, no prose or " +
                "markdown, exactly this shape: {\"scores\":{\"exterior\":0-100,\"interior\":0-100," +
                "\"safety\":0-100,\"cosmetic\":0-100,\"confidence\":0-100}," +
                "\"overallCondition\":\"EXCELLENT|GOOD|FAIR|POOR\",\"summary\":\"one or two sentences\"," +
                "\"integrity\":{\"missingImages\":[],\"duplicateImages\":[],\"lowQualityImages\":[]," +
                "\"suspiciousImages\":[],\"potentialFraud\":false}}. All five scores MUST be integers " +
                "between 0 and 100 (higher = better condition / higher confidence)."
    }
}
