package com.vsp.core.data.remote.rtdb

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.vsp.core.model.config.BrandingConfig
import com.vsp.core.model.config.ConfigGroup
import com.vsp.core.model.config.ConfigItem
import com.vsp.core.model.config.ConfigOption
import com.vsp.core.model.config.ConfigSection
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
 * The questionnaire is stored as a **fully expanded tree** so an admin can browse and edit each
 * category, group, and question as an individual node in the Firebase console (rather than one
 * opaque JSON string):
 *
 * ```
 * config/questionnaire/
 *   version, hash, updatedAt              // metadata
 *   sections/{sectionId}/                 // one node per inspection category
 *     id, title, order, appliesTo
 *     groups/{groupId}/                   // one node per group
 *       id, title, order
 *       items/{itemId}/                   // one node per question
 *         id, label, responseType, appliesTo, unit?, mandatory, allowImage, maxImages, order
 *         options[]                       // only when the question has choices
 * ```
 *
 * Child collections are keyed by their stable id (not array indices) so edits are targeted and
 * ordering is driven by each element's `order` field. The vehicle catalog remains a compact JSON
 * payload. All access is best-effort and guarded so failures degrade to offline baseline mode.
 */
@Singleton
class RtdbConfigSource @Inject constructor(
    private val firebase: FirebaseInitializer,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun fetchQuestionnaire(): QuestionnaireConfig? {
        val db = firebase.database() ?: return null
        firebase.ensureAuth()
        return runCatching {
            val snap = db.getReference(PATH_QUESTIONNAIRE).get().await()
            when {
                !snap.exists() -> null
                snap.hasChild("sections") -> parseQuestionnaire(snap)
                // Legacy single-blob format: decode it and migrate the node to the expanded tree.
                snap.hasChild("json") -> snap.child("json").asString()
                    ?.let { runCatching { json.decodeFromString<QuestionnaireConfig>(it) }.getOrNull() }
                    ?.also { migrated ->
                        runCatching {
                            db.getReference(PATH_QUESTIONNAIRE).setValue(questionnaireToMap(migrated)).await()
                        }.onFailure { Log.w(TAG, "Questionnaire tree migration failed", it) }
                    }
                else -> null
            }
        }.onFailure { Log.w(TAG, "RTDB read failed for $PATH_QUESTIONNAIRE", it) }.getOrNull()
    }

    suspend fun seedQuestionnaire(config: QuestionnaireConfig): Boolean {
        val db = firebase.database() ?: return false
        firebase.ensureAuth()
        return runCatching {
            db.getReference(PATH_QUESTIONNAIRE).setValue(questionnaireToMap(config)).await()
            true
        }.onFailure { Log.w(TAG, "RTDB write failed for $PATH_QUESTIONNAIRE", it) }.getOrDefault(false)
    }

    suspend fun fetchVehicleCatalog(): VehicleCatalog? = readConfigJson(PATH_VEHICLE_CATALOG)?.let {
        runCatching { json.decodeFromString<VehicleCatalog>(it) }
            .onFailure { e -> Log.w(TAG, "Malformed vehicle catalog", e) }
            .getOrNull()
    }

    suspend fun seedVehicleCatalog(catalog: VehicleCatalog): Boolean =
        writeConfig(PATH_VEHICLE_CATALOG, catalog.version, catalog.hash, catalog.updatedAt, json.encodeToString(catalog))

    /**
     * Reads vendor branding. Supports the three shapes an admin might create in the console:
     *  1. expanded fields directly under `config/branding` (companyName, primaryColor, …) — preferred;
     *  2. those same fields nested under a `json` object;
     *  3. a legacy single stringified-JSON blob at `config/branding/json`.
     */
    suspend fun fetchBranding(): BrandingConfig? {
        val db = firebase.database() ?: return null
        firebase.ensureAuth()
        val snap = runCatching { db.getReference(PATH_BRANDING).get().await() }
            .onFailure { Log.w(TAG, "RTDB read failed for $PATH_BRANDING", it) }
            .getOrNull() ?: return null
        if (!snap.exists()) return null

        val jsonChild = snap.child("json")
        if (jsonChild.exists()) {
            // A stringified blob (with tolerance for escaped quotes), or an object of fields.
            runCatching { jsonChild.getValue(String::class.java) }.getOrNull()
                ?.let { return decodeBrandingBlob(it) }
            return brandingFromFields(jsonChild)
        }
        return brandingFromFields(snap)
    }

    private fun brandingFromFields(snap: DataSnapshot): BrandingConfig {
        val d = BrandingConfig.DEFAULT
        return BrandingConfig(
            companyName = snap.child("companyName").asString() ?: d.companyName,
            tagline = snap.child("tagline").asString() ?: d.tagline,
            logoUrl = snap.child("logoUrl").asString() ?: d.logoUrl,
            primaryColor = snap.child("primaryColor").asString() ?: d.primaryColor,
            secondaryColor = snap.child("secondaryColor").asString() ?: d.secondaryColor,
            accentColor = snap.child("accentColor").asString() ?: d.accentColor,
            version = snap.child("version").asInt() ?: d.version,
            hash = snap.child("hash").asString() ?: d.hash,
            updatedAt = snap.child("updatedAt").asLong() ?: d.updatedAt,
        )
    }

    /** Decodes a stringified branding blob, tolerating console-entered escaped quotes. */
    private fun decodeBrandingBlob(raw: String): BrandingConfig? {
        runCatching { json.decodeFromString<BrandingConfig>(raw) }.getOrNull()?.let { return it }
        val unescaped = runCatching { json.decodeFromString<String>("\"$raw\"") }.getOrNull()
            ?: raw.replace("\\\"", "\"").replace("\\\\", "\\")
        return runCatching { json.decodeFromString<BrandingConfig>(unescaped) }
            .onFailure { Log.w(TAG, "Malformed branding config: ${it.message}") }
            .getOrNull()
    }

    suspend fun seedBranding(branding: BrandingConfig): Boolean {
        val db = firebase.database() ?: return false
        firebase.ensureAuth()
        val value = mapOf(
            "companyName" to branding.companyName,
            "tagline" to branding.tagline,
            "logoUrl" to branding.logoUrl,
            "primaryColor" to branding.primaryColor,
            "secondaryColor" to branding.secondaryColor,
            "accentColor" to branding.accentColor,
            "version" to branding.version,
            "hash" to branding.hash,
            "updatedAt" to branding.updatedAt,
        )
        return runCatching { db.getReference(PATH_BRANDING).setValue(value).await(); true }
            .onFailure { Log.w(TAG, "RTDB write failed for $PATH_BRANDING", it) }
            .getOrDefault(false)
    }

    // ---- Questionnaire tree <-> RTDB map ------------------------------------

    private fun questionnaireToMap(config: QuestionnaireConfig): Map<String, Any?> = mapOf(
        "version" to config.version,
        "hash" to config.hash,
        "updatedAt" to config.updatedAt,
        "sections" to config.sections.associate { it.id to sectionToMap(it) },
    )

    private fun sectionToMap(section: ConfigSection): Map<String, Any?> = mapOf(
        "id" to section.id,
        "title" to section.title,
        "order" to section.order,
        "appliesTo" to section.appliesTo,
        "groups" to section.groups.associate { it.id to groupToMap(it) },
    )

    private fun groupToMap(group: ConfigGroup): Map<String, Any?> = mapOf(
        "id" to group.id,
        "title" to group.title,
        "order" to group.order,
        "items" to group.items.associate { it.id to itemToMap(it) },
    )

    private fun itemToMap(item: ConfigItem): Map<String, Any?> = buildMap {
        put("id", item.id)
        put("label", item.label)
        put("responseType", item.responseType)
        put("appliesTo", item.appliesTo)
        item.unit?.let { put("unit", it) }
        put("mandatory", item.mandatory)
        put("allowImage", item.allowImage)
        put("maxImages", item.maxImages)
        put("order", item.order)
        if (item.options.isNotEmpty()) put("options", item.options.map(::optionToMap))
    }

    private fun optionToMap(option: ConfigOption): Map<String, Any?> = mapOf(
        "value" to option.value,
        "label" to option.label,
        "order" to option.order,
    )

    private fun parseQuestionnaire(snap: DataSnapshot): QuestionnaireConfig? {
        val version = snap.child("version").asInt() ?: return null
        return QuestionnaireConfig(
            version = version,
            hash = snap.child("hash").asString().orEmpty(),
            updatedAt = snap.child("updatedAt").asLong() ?: 0L,
            sections = snap.child("sections").children
                .mapNotNull(::parseSection)
                .sortedBy { it.order },
        )
    }

    private fun parseSection(snap: DataSnapshot): ConfigSection? {
        val id = snap.child("id").asString() ?: snap.key ?: return null
        return ConfigSection(
            id = id,
            title = snap.child("title").asString().orEmpty(),
            order = snap.child("order").asInt() ?: 0,
            appliesTo = snap.child("appliesTo").asString() ?: "BOTH",
            groups = snap.child("groups").children.mapNotNull(::parseGroup).sortedBy { it.order },
        )
    }

    private fun parseGroup(snap: DataSnapshot): ConfigGroup? {
        val id = snap.child("id").asString() ?: snap.key ?: return null
        return ConfigGroup(
            id = id,
            title = snap.child("title").asString().orEmpty(),
            order = snap.child("order").asInt() ?: 0,
            items = snap.child("items").children.mapNotNull(::parseItem).sortedBy { it.order },
        )
    }

    private fun parseItem(snap: DataSnapshot): ConfigItem? {
        val id = snap.child("id").asString() ?: snap.key ?: return null
        val responseType = snap.child("responseType").asString() ?: return null
        return ConfigItem(
            id = id,
            label = snap.child("label").asString().orEmpty(),
            responseType = responseType,
            appliesTo = snap.child("appliesTo").asString() ?: "BOTH",
            unit = snap.child("unit").asString(),
            mandatory = snap.child("mandatory").asBoolean() ?: false,
            allowImage = snap.child("allowImage").asBoolean() ?: false,
            maxImages = snap.child("maxImages").asInt() ?: 0,
            order = snap.child("order").asInt() ?: 0,
            options = snap.child("options").children.mapNotNull(::parseOption).sortedBy { it.order },
        )
    }

    private fun parseOption(snap: DataSnapshot): ConfigOption? {
        val value = snap.child("value").asString() ?: return null
        return ConfigOption(
            value = value,
            label = snap.child("label").asString().orEmpty(),
            order = snap.child("order").asInt() ?: 0,
        )
    }

    private fun DataSnapshot.asString(): String? = getValue(String::class.java)
    private fun DataSnapshot.asLong(): Long? = getValue(Long::class.java)
    private fun DataSnapshot.asInt(): Int? = getValue(Long::class.java)?.toInt()
    private fun DataSnapshot.asBoolean(): Boolean? = getValue(Boolean::class.java)

    // ---- Vehicle catalog (compact JSON payload) -----------------------------

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
        private const val PATH_BRANDING = "config/branding"
    }
}
