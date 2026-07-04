package com.vsp.core.domain.usecase

import com.vsp.core.domain.repository.ConfigRepository
import com.vsp.core.domain.repository.InspectionRepository
import com.vsp.core.model.AppResult
import com.vsp.core.model.config.BrandingConfig
import com.vsp.core.model.config.QuestionnaireConfig
import javax.inject.Inject

/**
 * Synchronizes vendor configuration at a login boundary. Adoption of a newer questionnaire version
 * is gated inside [ConfigRepository] (only when there is no local inspection data or on re-login),
 * so calling this on every login is safe: existing inspections keep their pinned snapshot.
 */
class SyncConfigUseCase @Inject constructor(
    private val configRepository: ConfigRepository,
) {
    suspend operator fun invoke(): AppResult<QuestionnaireConfig> = configRepository.syncOnLogin()
}

class GetActiveQuestionnaireUseCase @Inject constructor(
    private val configRepository: ConfigRepository,
) {
    suspend operator fun invoke(): QuestionnaireConfig = configRepository.activeQuestionnaire()
}

/** Returns the active vendor report branding/theme (cache → default fallback). */
class GetActiveBrandingUseCase @Inject constructor(
    private val configRepository: ConfigRepository,
) {
    suspend operator fun invoke(): BrandingConfig = configRepository.activeBranding()
}

/**
 * Returns the questionnaire an inspection should render, reading the inspection's pinned snapshot
 * (so later Firebase edits never mutate an in-flight inspection) and falling back to the active
 * configuration when no snapshot exists.
 */
class GetInspectionQuestionnaireUseCase @Inject constructor(
    private val inspectionRepository: InspectionRepository,
) {
    suspend operator fun invoke(inspectionId: String): QuestionnaireConfig =
        inspectionRepository.questionnaireFor(inspectionId)
}

/** Per-question photo limit ([maxImages]) for a given item, read from the inspection's snapshot. */
class GetItemImageLimitUseCase @Inject constructor(
    private val inspectionRepository: InspectionRepository,
) {
    data class Limit(val allowImage: Boolean, val maxImages: Int)

    suspend operator fun invoke(inspectionId: String, itemId: String?, fallbackMax: Int): Limit {
        if (itemId == null) return Limit(allowImage = true, maxImages = fallbackMax)
        val item = inspectionRepository.questionnaireFor(inspectionId).item(itemId)
            ?: return Limit(allowImage = true, maxImages = fallbackMax)
        val max = if (item.allowImage) item.maxImages.takeIf { it > 0 } ?: fallbackMax else 0
        return Limit(allowImage = item.allowImage, maxImages = max)
    }
}
