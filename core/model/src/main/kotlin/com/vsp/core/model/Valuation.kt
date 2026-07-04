package com.vsp.core.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Buy/sell decision summary derived from the inspection outcome: an estimated condition score
 * (0–100), how it compares to a typical vehicle of its class, and plain-language guidance. Embedded
 * in the report JSON and rendered in both the in-app report and the PDF.
 */
@Serializable
data class Valuation(
    /** Estimated overall condition, 0–100 (higher is better). */
    val overallScore: Int,
    /** Human band for the score: Excellent / Good / Fair / Poor. */
    val conditionBand: String,
    /** Reference score for a typical vehicle of this class. */
    val benchmarkScore: Int,
    /** overallScore − benchmarkScore. */
    val deltaVsTypical: Int,
    /** "Above typical" / "Around typical" / "Below typical". */
    val marketPosition: String,
    /** Buy/sell guidance sentence. */
    val verdict: String,
    /** Pricing guidance relative to typical asking price. */
    val priceGuidance: String,
    /** Total damage marks (AI findings + manual annotations) considered. */
    val damageCount: Int,
)

/**
 * Pure, deterministic valuation logic shared by the report JSON builder, the PDF generator, and the
 * in-app report screen so all three surfaces agree.
 */
object ValuationCalculator {

    /** Typical used-vehicle baseline (~3.5/5). Vehicles above this compare favorably. */
    const val BENCHMARK = 70

    /**
     * @param overallRating an explicit 1–5 overall rating, or null to derive from [categoryRatings].
     * @param categoryRatings category → 1–5 ratings (from the final assessment).
     * @param damageCount total AI findings + manual annotations.
     * @param highSeverityCount findings/annotations with HIGH or CRITICAL severity.
     */
    fun compute(
        overallRating: Int?,
        categoryRatings: Map<String, Int>,
        damageCount: Int,
        highSeverityCount: Int,
    ): Valuation? {
        val baseRating = overallRating
            ?: categoryRatings.values.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            ?: return null

        val ratingScore = (baseRating.coerceIn(1, 5) / 5.0 * 100).roundToInt()
        val penalty = highSeverityCount * 5 + (damageCount - highSeverityCount).coerceAtLeast(0) * 2
        val overallScore = (ratingScore - penalty).coerceIn(0, 100)

        val band = when {
            overallScore >= 85 -> "Excellent"
            overallScore >= 70 -> "Good"
            overallScore >= 50 -> "Fair"
            else -> "Poor"
        }
        val delta = overallScore - BENCHMARK
        val position = when {
            delta >= 8 -> "Above typical"
            delta <= -8 -> "Below typical"
            else -> "Around typical"
        }
        val verdict = when (band) {
            "Excellent" -> "Excellent condition — a strong buy at or above typical market value."
            "Good" -> "Good condition — a sound purchase with only minor negotiation room."
            "Fair" -> "Fair condition — negotiate on price and budget for the noted repairs."
            else -> "Below-par condition — significant issues found; proceed with caution."
        }
        val priceGuidance = when {
            delta >= 8 -> "Condition supports pricing at or slightly above the typical asking price."
            delta <= -8 -> "Condition suggests pricing below typical asking; factor in repair costs."
            else -> "Condition is in line with the typical asking price for similar vehicles."
        }
        return Valuation(
            overallScore = overallScore,
            conditionBand = band,
            benchmarkScore = BENCHMARK,
            deltaVsTypical = delta,
            marketPosition = position,
            verdict = verdict,
            priceGuidance = priceGuidance,
            damageCount = damageCount,
        )
    }
}
