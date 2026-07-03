package com.vsp.core.data.ai

import android.util.Base64
import android.util.Log
import com.vsp.core.domain.port.AiPrompt
import com.vsp.core.domain.port.AiVisionPort
import com.vsp.core.domain.port.RawAiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * Gemini Vision implementation of [AiVisionPort] using the Generative Language REST API.
 * The response text is expected to be a JSON payload matching contracts/ai-gemini-contract.md;
 * it is returned raw and validated downstream by [AiResponseValidator].
 *
 * Throws [AiUnavailableException] when no API key is configured so the repository can map it to
 * [com.vsp.core.model.AppError.AiUnavailable] and preserve offline-first behavior.
 */
class GeminiAiVisionPort @Inject constructor(
    private val config: AiConfig,
) : AiVisionPort {

    class AiUnavailableException(message: String) : Exception(message)

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val TAG = "GeminiAiVision"
        // Upper bound on images attached to a single final-verification request.
        const val MAX_FINAL_IMAGES = 12
    }

    override suspend fun detect(imageBytes: ByteArray, prompt: AiPrompt): RawAiResponse =
        generate("detect", prompt, listOf(imageBytes))

    override suspend fun reverify(imageBytes: ByteArray, prompt: AiPrompt): RawAiResponse =
        generate("reverify", prompt, listOf(imageBytes))

    override suspend fun finalVerify(prompt: AiPrompt, imageRefs: List<String>): RawAiResponse {
        // Attach the actual captured images (local files) so the model can score visually.
        // Capped to keep the request within reasonable size/latency bounds.
        val images = imageRefs.asSequence()
            .mapNotNull { ref -> runCatching { File(ref).takeIf { it.exists() }?.readBytes() }.getOrNull() }
            .take(MAX_FINAL_IMAGES)
            .toList()
        return generate("finalVerify", prompt, images)
    }

    private suspend fun generate(
        operation: String,
        prompt: AiPrompt,
        imageBytesList: List<ByteArray>,
    ): RawAiResponse =
        withContext(Dispatchers.IO) {
            if (!config.isConfigured) {
                Log.w(TAG, "[$operation] skipped: Gemini API key not configured")
                throw AiUnavailableException("Gemini API key not configured")
            }

            val instruction = prompt.instruction
            val totalImageBytes = imageBytesList.sumOf { it.size }
            Log.i(
                TAG,
                "[$operation] request → model=${config.model}, section=${prompt.section}, " +
                    "position=${prompt.position}, images=${imageBytesList.size}, imageBytes=$totalImageBytes, " +
                    "promptChars=${instruction.length}",
            )
            Log.d(TAG, "[$operation] prompt=${instruction.take(4000)}")
            val startedAt = System.currentTimeMillis()

            val endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=${config.apiKey}"
            val parts = JSONArray().apply {
                put(JSONObject().put("text", instruction))
                imageBytesList.forEach { bytes ->
                    put(
                        JSONObject().put(
                            "inline_data",
                            JSONObject()
                                .put("mime_type", "image/jpeg")
                                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)),
                        ),
                    )
                }
            }
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put(
                    "generationConfig",
                    JSONObject().put("responseMimeType", "application/json").put("temperature", 0.1),
                )
                .toString()

            // Multi-image (final verification) requests need a longer read window.
            val readTimeoutMs = if (imageBytesList.size > 1) 120_000 else 45_000
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30_000
                readTimeout = readTimeoutMs
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                connection.outputStream.use { it.write(body.toByteArray()) }
                val code = connection.responseCode
                val elapsedMs = System.currentTimeMillis() - startedAt
                if (code !in 200..299) {
                    val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    Log.e(TAG, "[$operation] response ← HTTP $code in ${elapsedMs}ms: ${err.take(500)}")
                    throw AiUnavailableException("Gemini HTTP $code: $err")
                }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val text = extractText(response)
                Log.i(
                    TAG,
                    "[$operation] response ← HTTP $code in ${elapsedMs}ms, outputChars=${text.length}",
                )
                Log.d(TAG, "[$operation] output=${text.take(1000)}")
                RawAiResponse(text)
            } catch (e: AiUnavailableException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "[$operation] request failed after ${System.currentTimeMillis() - startedAt}ms", e)
                throw e
            } finally {
                connection.disconnect()
            }
        }

    /** Pulls the model's text output out of the Generative Language envelope. */
    private fun extractText(response: String): String = runCatching {
        json.parseToJsonElement(response).jsonObject["candidates"]
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
    }.getOrNull() ?: response
}
