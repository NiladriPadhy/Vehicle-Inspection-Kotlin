package com.vsp.core.data.ai

import kotlinx.serialization.Serializable

/** Wire model for the Gemini damage-detection response (see contracts/ai-gemini-contract.md). */
@Serializable
data class AiDetectionResponseDto(
    val findings: List<AiFindingDto> = emptyList(),
)

@Serializable
data class AiFindingDto(
    val damageType: String? = null,
    val confidence: Float? = null,
    val severity: String? = null,
    val boundingBox: AiBoundingBoxDto? = null,
    val repairRecommendation: String? = null,
)

@Serializable
data class AiBoundingBoxDto(
    val x: Float? = null,
    val y: Float? = null,
    val w: Float? = null,
    val h: Float? = null,
)

/** Wire model for annotation re-verification. */
@Serializable
data class AiReverifyResponseDto(
    val confirmed: Boolean? = null,
    val correctedDamageType: String? = null,
    val correctedSeverity: String? = null,
    val inconsistency: Boolean? = null,
)

/** Wire model for whole-inspection final verification. */
@Serializable
data class AiFinalVerificationDto(
    val scores: AiScoresDto? = null,
    val overallCondition: String? = null,
    val summary: String? = null,
    val integrity: AiIntegrityDto? = null,
)

@Serializable
data class AiScoresDto(
    // Doubles so responses like 85 or 85.0 or 0.85 all parse; normalized in the validator.
    val exterior: Double? = null,
    val interior: Double? = null,
    val safety: Double? = null,
    val cosmetic: Double? = null,
    val confidence: Double? = null,
)

@Serializable
data class AiIntegrityDto(
    val missingImages: List<String> = emptyList(),
    val duplicateImages: List<String> = emptyList(),
    val lowQualityImages: List<String> = emptyList(),
    val suspiciousImages: List<String> = emptyList(),
    val potentialFraud: Boolean = false,
)
