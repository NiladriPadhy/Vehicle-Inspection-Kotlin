package com.vsp.core.domain.port

import com.vsp.core.model.Vehicle

/** Raw, unvalidated AI response text plus context for validation in the data layer. */
data class RawAiResponse(val rawJson: String)

/** Prompt context for an AI vision request. */
data class AiPrompt(val section: String, val position: String, val instruction: String)

/** Abstraction over the Gemini Vision model. Implemented in :core:data. */
interface AiVisionPort {
    suspend fun detect(imageBytes: ByteArray, prompt: AiPrompt): RawAiResponse
    suspend fun reverify(imageBytes: ByteArray, prompt: AiPrompt): RawAiResponse
    suspend fun finalVerify(prompt: AiPrompt, imageRefs: List<String>): RawAiResponse
}

/** Provider-agnostic VIN decode source. Implemented in :core:data. */
interface VinDecodeSource {
    suspend fun decode(vin: String): Vehicle?
}
