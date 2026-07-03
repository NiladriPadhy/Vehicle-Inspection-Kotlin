package com.vsp.core.data.vin

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device VIN OCR via ML Kit text recognition. Extracts a 17-character VIN candidate from a
 * captured image, normalizing commonly confused characters (I/O/Q are invalid in VINs).
 */
@Singleton
class VinOcrScanner @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scan(imagePath: String): String? {
        val bitmap = BitmapFactory.decodeFile(imagePath) ?: return null
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = suspendCancellableCoroutine<String> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return extractVin(text)
    }

    fun extractVin(text: String): String? {
        val cleaned = text.uppercase()
            .replace('O', '0')
            .replace('Q', '0')
            .replace('I', '1')
        return VIN_REGEX.findAll(cleaned).map { it.value }.firstOrNull()
    }

    companion object {
        private val VIN_REGEX = Regex("[A-HJ-NPR-Z0-9]{17}")
    }
}
