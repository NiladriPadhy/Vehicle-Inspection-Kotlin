package com.vsp.core.data.config

import com.vsp.core.data.local.dao.ConfigCacheDao
import com.vsp.core.data.local.dao.InspectionDao
import com.vsp.core.data.local.entity.ConfigCacheEntity
import com.vsp.core.data.remote.rtdb.RtdbConfigSource
import com.vsp.core.domain.coroutine.DispatcherProvider
import com.vsp.core.domain.repository.ConfigRepository
import com.vsp.core.model.AppResult
import com.vsp.core.model.config.BrandingConfig
import com.vsp.core.model.config.ConfigHashing
import com.vsp.core.model.config.QuestionnaireConfig
import com.vsp.core.model.config.VehicleCatalog
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns vendor configuration with version pinning and gated adoption (feature 002 §9–§11).
 *
 * - [activeQuestionnaire] serves the locally cached active version, falling back to the bundled
 *   baseline when no cache exists (so the app is always usable, even fully offline).
 * - [syncOnLogin] runs at the login boundary: it fetches the remote questionnaire (seeding the
 *   baseline for a brand-new vendor DB) and adopts it as the new active version. Adoption at login
 *   is safe because existing inspections keep their pinned snapshot. When there is no local
 *   inspection data adoption is likewise always allowed.
 */
@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val rtdb: RtdbConfigSource,
    private val configCacheDao: ConfigCacheDao,
    private val inspectionDao: InspectionDao,
    private val baselineProvider: BaselineProvider,
    private val dispatchers: DispatcherProvider,
) : ConfigRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun activeQuestionnaire(): QuestionnaireConfig = withContext(dispatchers.io) {
        cachedQuestionnaire() ?: baselineProvider.questionnaire()
    }

    override suspend fun syncOnLogin(): AppResult<QuestionnaireConfig> = withContext(dispatchers.io) {
        val remote = rtdb.fetchQuestionnaire()
        val hasLocalData = inspectionDao.count() > 0

        val adopted = when {
            // Vendor DB has a questionnaire → adopt it (login boundary permits adoption).
            remote != null -> normalize(remote).also { cache(QUESTIONNAIRE, it.version, it.hash, json.encodeToString(it)) }

            // No remote yet and nothing cached → this is a fresh vendor: seed the baseline and adopt.
            cachedQuestionnaire() == null -> {
                val baseline = baselineProvider.questionnaire()
                rtdb.seedQuestionnaire(baseline) // best-effort; ignored when offline
                cache(QUESTIONNAIRE, baseline.version, baseline.hash, json.encodeToString(baseline))
                baseline
            }

            // Remote unreachable but we already have a cached active version → keep it.
            else -> cachedQuestionnaire()!!
        }

        // Best-effort vehicle catalog + branding refresh (non-fatal). For a fresh vendor DB with no
        // branding yet, seed the bundled baseline branding so the console is pre-populated.
        rtdb.fetchVehicleCatalog()?.let { cache(VEHICLE_CATALOG, it.version, it.hash, json.encodeToString(it)) }
        val branding = rtdb.fetchBranding() ?: baselineProvider.branding().also { rtdb.seedBranding(it) }
        cache(BRANDING, branding.version, branding.hash, json.encodeToString(branding))

        // hasLocalData is not required to gate adoption at a login boundary, but recorded for clarity.
        @Suppress("UNUSED_EXPRESSION") hasLocalData
        AppResult.Success(adopted)
    }

    override suspend fun vehicleCatalog(): VehicleCatalog? = withContext(dispatchers.io) {
        rtdb.fetchVehicleCatalog()?.also { cache(VEHICLE_CATALOG, it.version, it.hash, json.encodeToString(it)) }
            ?: configCacheDao.get(VEHICLE_CATALOG)?.let {
                runCatching { json.decodeFromString<VehicleCatalog>(it.json) }.getOrNull()
            }
    }

    override suspend fun activeBranding(): BrandingConfig = withContext(dispatchers.io) {
        // Fetch live (best-effort) so branding edits appear without requiring a re-login, then fall
        // back to the last cached value, then the bundled baseline branding (offline/first-run).
        rtdb.fetchBranding()?.also { cache(BRANDING, it.version, it.hash, json.encodeToString(it)) }
            ?: configCacheDao.get(BRANDING)?.let {
                runCatching { json.decodeFromString<BrandingConfig>(it.json) }.getOrNull()
            }
            ?: baselineProvider.branding()
    }

    private suspend fun cachedQuestionnaire(): QuestionnaireConfig? =
        configCacheDao.get(QUESTIONNAIRE)?.let {
            runCatching { json.decodeFromString<QuestionnaireConfig>(it.json) }.getOrNull()
        }

    /** Ensures the stored config carries an authoritative content hash. */
    private fun normalize(config: QuestionnaireConfig): QuestionnaireConfig =
        config.copy(hash = ConfigHashing.hash(config))

    private suspend fun cache(type: String, version: Int, hash: String, jsonPayload: String) {
        configCacheDao.upsert(
            ConfigCacheEntity(type, version, hash, jsonPayload, System.currentTimeMillis()),
        )
    }

    companion object {
        private const val QUESTIONNAIRE = "QUESTIONNAIRE"
        private const val VEHICLE_CATALOG = "VEHICLE_CATALOG"
        private const val BRANDING = "BRANDING"
    }
}
