package com.vsp.core.data.config

import android.content.Context
import com.vsp.core.model.config.BaselineQuestionnaire
import com.vsp.core.model.config.BrandingConfig
import com.vsp.core.model.config.ConfigHashing
import com.vsp.core.model.config.QuestionnaireConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the baseline questionnaire for a first-time vendor. Prefers the bundled, editable asset
 * `assets/baseline_questionnaire.json` (so a vendor can customise the seed without recompiling) and
 * falls back to the in-code [BaselineQuestionnaire] derived from the checklist catalog. The content
 * hash is always recomputed so an edited asset stays self-consistent.
 */
@Singleton
class BaselineProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun questionnaire(): QuestionnaireConfig {
        val fromAsset = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
                .let { json.decodeFromString<QuestionnaireConfig>(it) }
                .let { it.copy(hash = ConfigHashing.hash(it)) }
        }.getOrNull()
        return fromAsset ?: BaselineQuestionnaire.build()
    }

    /**
     * Baseline vendor branding for a first-time/offline vendor. Prefers the bundled, editable asset
     * `assets/baseline_branding.json` and falls back to the neutral [BrandingConfig.DEFAULT].
     */
    fun branding(): BrandingConfig = runCatching {
        context.assets.open(BRANDING_ASSET_NAME).bufferedReader().use { it.readText() }
            .let { json.decodeFromString<BrandingConfig>(it) }
    }.getOrNull() ?: BrandingConfig.DEFAULT

    companion object {
        private const val ASSET_NAME = "baseline_questionnaire.json"
        private const val BRANDING_ASSET_NAME = "baseline_branding.json"
    }
}
