package com.vsp.core.data.ai

import com.vsp.core.model.AIFinding
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.BoundingBox
import com.vsp.core.model.DamageType
import com.vsp.core.model.FinalVerification
import com.vsp.core.model.FindingSource
import com.vsp.core.model.IntegrityFlags
import com.vsp.core.model.ReverifyResult
import com.vsp.core.model.Severity
import com.vsp.core.model.VerificationScores
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

/**
 * Validates and normalizes raw AI responses before they are allowed into the domain.
 * Enforces the constitution rule "all AI responses validated": strict JSON parsing,
 * damage-type whitelist, confidence gating, severity whitelist, and bounding-box bounds.
 * Pure and fully unit-tested (see AiResponseValidatorTest).
 */
class AiResponseValidator @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Findings at or above this confidence are auto-accepted; below [reviewThreshold] are dropped. */
    var acceptThreshold: Float = 0.70f
    var reviewThreshold: Float = 0.40f

    fun validateDetection(imageId: String, rawJson: String, now: Long): AppResult<List<AIFinding>> {
        val dto = parse<AiDetectionResponseDto>(rawJson) ?: return invalid("Malformed detection JSON")
        val findings = dto.findings.mapNotNull { it.toDomainOrNull(imageId, now) }
        return AppResult.Success(findings)
    }

    fun validateReverify(rawJson: String): AppResult<ReverifyResult> {
        val dto = parse<AiReverifyResponseDto>(rawJson) ?: return invalid("Malformed reverify JSON")
        val confirmed = dto.confirmed ?: return invalid("Missing 'confirmed'")
        return AppResult.Success(
            ReverifyResult(
                confirmed = confirmed,
                correctedDamageType = dto.correctedDamageType?.let(::damageTypeOrNull),
                correctedSeverity = dto.correctedSeverity?.let(::severityOrNull),
                inconsistency = dto.inconsistency ?: false,
            ),
        )
    }

    fun validateFinal(rawJson: String): AppResult<FinalVerification> {
        val dto = parse<AiFinalVerificationDto>(rawJson) ?: return invalid("Malformed final JSON")
        val s = dto.scores ?: return invalid("Missing scores")
        val scores = VerificationScores(
            exterior = clampScore(s.exterior) ?: return invalid("Missing exterior score"),
            interior = clampScore(s.interior) ?: return invalid("Missing interior score"),
            safety = clampScore(s.safety) ?: return invalid("Missing safety score"),
            cosmetic = clampScore(s.cosmetic) ?: return invalid("Missing cosmetic score"),
            confidence = clampScore(s.confidence) ?: return invalid("Missing confidence score"),
        )
        val integrity = dto.integrity?.let {
            IntegrityFlags(
                missingImages = it.missingImages,
                duplicateImages = it.duplicateImages,
                lowQualityImages = it.lowQualityImages,
                suspiciousImages = it.suspiciousImages,
                potentialFraud = it.potentialFraud,
            )
        } ?: IntegrityFlags()
        return AppResult.Success(
            FinalVerification(
                scores = scores,
                overallCondition = dto.overallCondition?.takeIf { it.isNotBlank() } ?: "UNKNOWN",
                summary = dto.summary.orEmpty(),
                integrity = integrity,
            ),
        )
    }

    private fun AiFindingDto.toDomainOrNull(imageId: String, now: Long): AIFinding? {
        val type = damageTypeOrNull(damageType ?: return null) ?: DamageType.OTHER
        val conf = confidence ?: return null
        if (conf.isNaN() || conf < reviewThreshold) return null
        val sev = severityOrNull(severity ?: return null) ?: return null
        val box = boundingBox?.toDomainOrNull() ?: return null
        return AIFinding(
            id = UUID.randomUUID().toString(),
            imageId = imageId,
            damageType = type,
            confidence = conf.coerceIn(0f, 1f),
            severity = sev,
            boundingBox = box,
            repairRecommendation = repairRecommendation.orEmpty(),
            reviewRequired = conf < acceptThreshold,
            source = FindingSource.INITIAL,
            createdAt = now,
        )
    }

    private fun AiBoundingBoxDto.toDomainOrNull(): BoundingBox? {
        val nx = x ?: return null
        val ny = y ?: return null
        val nw = w ?: return null
        val nh = h ?: return null
        if (listOf(nx, ny, nw, nh).any { it.isNaN() }) return null
        if (nw <= 0f || nh <= 0f) return null
        if (nx < 0f || ny < 0f || nx + nw > 1.0001f || ny + nh > 1.0001f) return null
        return BoundingBox(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f), nw.coerceIn(0f, 1f), nh.coerceIn(0f, 1f))
    }

    private inline fun <reified T> parse(raw: String): T? =
        runCatching { json.decodeFromString<T>(raw) }.getOrNull()

    private fun damageTypeOrNull(value: String): DamageType? =
        DamageType.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }

    private fun severityOrNull(value: String): Severity? =
        Severity.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }

    /**
     * Normalizes a raw score to an integer 0..100. Accepts 0..100 values directly and treats a
     * fractional 0..1 value as a percentage (e.g. 0.85 -> 85) for models that reply that way.
     */
    private fun clampScore(value: Double?): Int? {
        if (value == null || value.isNaN()) return null
        val scaled = if (value > 0.0 && value < 1.0) value * 100.0 else value
        return scaled.toInt().coerceIn(0, 100)
    }

    private fun <T> invalid(message: String): AppResult<T> =
        AppResult.Failure(AppError.AiInvalidResponse(message))
}
