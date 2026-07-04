package com.vsp.core.model.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Emits the bundled baseline questionnaire to `app/src/main/assets/baseline_questionnaire.json`,
 * keeping the shipped asset byte-for-byte consistent with [BaselineQuestionnaire] (derived from
 * [com.vsp.core.model.catalog.ChecklistCatalog]). Run via `gradle :core:model:test` after changing
 * the catalog. Not a behavioural assertion — it regenerates a checked-in asset.
 */
class BaselineAssetGeneratorTest {

    @Test
    fun `generate baseline questionnaire asset`() {
        val json = Json { prettyPrint = true; encodeDefaults = true }
        val baseline = BaselineQuestionnaire.build()
        val assetsDir = File(repoRoot(), "app/src/main/assets").apply { mkdirs() }
        val target = File(assetsDir, "baseline_questionnaire.json")
        target.writeText(json.encodeToString(baseline))
        assertTrue("baseline asset written", target.exists() && target.length() > 0)
    }

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return dir
    }
}
