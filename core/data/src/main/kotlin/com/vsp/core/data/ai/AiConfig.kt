package com.vsp.core.data.ai

/**
 * Runtime configuration for the Gemini Vision integration. Injected so the API key can be
 * sourced from BuildConfig / secure storage rather than hard-coded. When [apiKey] is blank
 * the port reports the model as unavailable (offline-first: the pipeline degrades gracefully).
 */
data class AiConfig(
    val apiKey: String = "",
    val model: String = "gemini-2.5-flash",
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}
