package com.vsp.core.data.ai

import com.google.common.truth.Truth.assertThat
import com.vsp.core.model.AppResult
import org.junit.Test

class AiResponseValidatorTest {

    private val validator = AiResponseValidator()

    @Test
    fun `valid detection produces findings`() {
        val json = """
            {"findings":[
              {"damageType":"DENT","confidence":0.9,"severity":"HIGH","boundingBox":{"x":0.1,"y":0.1,"w":0.2,"h":0.2},"repairRecommendation":"Replace panel"}
            ]}
        """.trimIndent()

        val result = validator.validateDetection("img1", json, now = 1L)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val findings = (result as AppResult.Success).value
        assertThat(findings).hasSize(1)
        assertThat(findings[0].reviewRequired).isFalse()
    }

    @Test
    fun `low-confidence findings are dropped`() {
        val json = """{"findings":[{"damageType":"SCRATCH","confidence":0.1,"severity":"LOW","boundingBox":{"x":0,"y":0,"w":0.1,"h":0.1}}]}"""
        val result = validator.validateDetection("img1", json, now = 1L)
        assertThat((result as AppResult.Success).value).isEmpty()
    }

    @Test
    fun `mid-confidence finding is flagged for review`() {
        val json = """{"findings":[{"damageType":"RUST","confidence":0.5,"severity":"MEDIUM","boundingBox":{"x":0,"y":0,"w":0.1,"h":0.1}}]}"""
        val findings = (validator.validateDetection("img1", json, 1L) as AppResult.Success).value
        assertThat(findings).hasSize(1)
        assertThat(findings[0].reviewRequired).isTrue()
    }

    @Test
    fun `out-of-bounds bounding box is rejected`() {
        val json = """{"findings":[{"damageType":"DENT","confidence":0.9,"severity":"HIGH","boundingBox":{"x":0.9,"y":0.1,"w":0.5,"h":0.2}}]}"""
        val findings = (validator.validateDetection("img1", json, 1L) as AppResult.Success).value
        assertThat(findings).isEmpty()
    }

    @Test
    fun `unknown damage type maps to OTHER`() {
        val json = """{"findings":[{"damageType":"ALIEN_GOO","confidence":0.9,"severity":"HIGH","boundingBox":{"x":0,"y":0,"w":0.1,"h":0.1}}]}"""
        val findings = (validator.validateDetection("img1", json, 1L) as AppResult.Success).value
        assertThat(findings).hasSize(1)
        assertThat(findings[0].damageType.name).isEqualTo("OTHER")
    }

    @Test
    fun `malformed json fails`() {
        val result = validator.validateDetection("img1", "not json", 1L)
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
    }

    @Test
    fun `empty findings array is accepted`() {
        val result = validator.validateDetection("img1", """{"findings":[]}""", 1L)
        assertThat((result as AppResult.Success).value).isEmpty()
    }

    @Test
    fun `final verification clamps scores`() {
        val json = """{"scores":{"exterior":150,"interior":80,"safety":90,"cosmetic":70,"confidence":85},"overallCondition":"GOOD","summary":"ok"}"""
        val result = validator.validateFinal(json)
        val verification = (result as AppResult.Success).value
        assertThat(verification.scores.exterior).isEqualTo(100)
        assertThat(verification.scores.interior).isEqualTo(80)
    }

    @Test
    fun `reverify requires confirmed field`() {
        val ok = validator.validateReverify("""{"confirmed":true,"inconsistency":false}""")
        assertThat(ok).isInstanceOf(AppResult.Success::class.java)
        val bad = validator.validateReverify("""{"inconsistency":false}""")
        assertThat(bad).isInstanceOf(AppResult.Failure::class.java)
    }
}
