package com.vsp.core.data.remote.rtdb

import android.util.Log
import com.vsp.core.model.config.QuestionnaireConfig
import com.vsp.core.model.config.VehicleCatalog
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads/writes vendor configuration in Firebase RTDB.
 *
 * The questionnaire and vehicle catalog are stored as a versioned node carrying a serialized JSON
 * payload (`/config/questionnaire = { version, hash, updatedAt, json }`). Storing the definition as
 * one JSON string keeps the content hash stable and parsing robust; an admin edits the `json` value
 * (and bumps `version`) from the Firebase console. All reads are gated by anonymous auth and guarded
 * so failures degrade to offline baseline mode.
 */
@Singleton
class RtdbConfigSource @Inject constructor(
    private val firebase: FirebaseInitializer,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun fetchQuestionnaire(): QuestionnaireConfig? = readConfigJson(PATH_QUESTIONNAIRE)?.let {
        runCatching { json.decodeFromString<QuestionnaireConfig>(it) }
            .onFailure { e -> Log.w(TAG, "Malformed questionnaire config", e) }
            .getOrNull()
    }

    suspend fun fetchVehicleCatalog(): VehicleCatalog? = readConfigJson(PATH_VEHICLE_CATALOG)?.let {
        runCatching { json.decodeFromString<VehicleCatalog>(it) }
            .onFailure { e -> Log.w(TAG, "Malformed vehicle catalog", e) }
            .getOrNull()
    }

    suspend fun seedQuestionnaire(config: QuestionnaireConfig): Boolean =
        writeConfig(PATH_QUESTIONNAIRE, config.version, config.hash, config.updatedAt, json.encodeToString(config))

    suspend fun seedVehicleCatalog(catalog: VehicleCatalog): Boolean =
        writeConfig(PATH_VEHICLE_CATALOG, catalog.version, catalog.hash, catalog.updatedAt, json.encodeToString(catalog))

    private suspend fun readConfigJson(path: String): String? {
        val db = firebase.database() ?: return null
        firebase.ensureAuth()
        return runCatching { db.getReference(path).child("json").get().await().getValue(String::class.java) }
            .onFailure { Log.w(TAG, "RTDB read failed for $path", it) }
            .getOrNull()
    }

    private suspend fun writeConfig(path: String, version: Int, hash: String, updatedAt: Long, payload: String): Boolean {
        val db = firebase.database() ?: return false
        firebase.ensureAuth()
        val value = mapOf("version" to version, "hash" to hash, "updatedAt" to updatedAt, "json" to payload)
        return runCatching { db.getReference(path).setValue(value).await(); true }
            .onFailure { Log.w(TAG, "RTDB write failed for $path", it) }
            .getOrDefault(false)
    }

    companion object {
        private const val TAG = "RtdbConfigSource"
        private const val PATH_QUESTIONNAIRE = "config/questionnaire"
        private const val PATH_VEHICLE_CATALOG = "config/vehicleCatalog"
    }
}
