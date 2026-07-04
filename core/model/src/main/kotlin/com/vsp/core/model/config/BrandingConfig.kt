package com.vsp.core.model.config

import kotlinx.serialization.Serializable

/**
 * Vendor-specific report branding fetched from Firebase RTDB (`config/branding`). Drives the
 * report's company identity and theme colors so each vendor build produces a branded report.
 * All fields have safe defaults so the report renders identically to the previous fixed theme when
 * no branding is configured (offline baseline mode).
 *
 * Colors are hex strings (e.g. `#0D47A1`); invalid values fall back to the defaults at render time.
 */
@Serializable
data class BrandingConfig(
    /** Overridden company name; when blank the report falls back to the build-time VENDOR_ID. */
    val companyName: String = "",
    val tagline: String = "",
    /** Optional remote logo URL (reserved; not embedded yet). */
    val logoUrl: String = "",
    /** Primary brand color — cover gradient start, section numbers, key accents. */
    val primaryColor: String = DEFAULT_PRIMARY,
    /** Secondary brand color — cover gradient end. */
    val secondaryColor: String = DEFAULT_SECONDARY,
    /** Accent color — section headings and highlights. */
    val accentColor: String = DEFAULT_ACCENT,
    val version: Int = 0,
    val hash: String = "",
    val updatedAt: Long = 0L,
) {
    companion object {
        const val DEFAULT_PRIMARY = "#0D47A1"
        const val DEFAULT_SECONDARY = "#1565C0"
        const val DEFAULT_ACCENT = "#0B6E2E"

        /** The neutral, unbranded theme used when no vendor branding is configured. */
        val DEFAULT = BrandingConfig()
    }
}
