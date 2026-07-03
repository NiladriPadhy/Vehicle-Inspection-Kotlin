package com.vsp.core.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vsp.core.model.ImageQuality
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * On-device image quality gate. Rejects images that are too dark, overexposed, or blurry so the
 * inspector re-captures before AI analysis. Heuristics operate on a downscaled grayscale sample.
 */
@Singleton
class ImageQualityAnalyzer @Inject constructor() {

    fun analyze(imagePath: String): ImageQuality {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = BitmapFactory.decodeFile(imagePath, options) ?: return ImageQuality.INCOMPLETE
        return try {
            analyzeBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun analyzeBitmap(bitmap: Bitmap): ImageQuality {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 2 || h < 2) return ImageQuality.INCOMPLETE
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val luma = FloatArray(pixels.size)
        var sum = 0.0
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            luma[i] = y
            sum += y
        }
        val mean = sum / pixels.size
        if (mean < DARK_THRESHOLD) return ImageQuality.DARK
        if (mean > BRIGHT_THRESHOLD) return ImageQuality.OVEREXPOSED

        if (varibanceOfLaplacian(luma, w, h) < BLUR_THRESHOLD) return ImageQuality.BLURRY
        return ImageQuality.OK
    }

    /** Approximate focus measure: variance of a 4-neighbor Laplacian over the luma channel. */
    private fun varibanceOfLaplacian(luma: FloatArray, w: Int, h: Int): Double {
        var sum = 0.0
        var sqSum = 0.0
        var count = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val lap = 4 * luma[i] - luma[i - 1] - luma[i + 1] - luma[i - w] - luma[i + w]
                sum += lap
                sqSum += lap * lap
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        return sqSum / count - mean * mean
    }

    companion object {
        private const val DARK_THRESHOLD = 45.0
        private const val BRIGHT_THRESHOLD = 235.0
        private const val BLUR_THRESHOLD = 120.0

        @Suppress("unused")
        private fun std(v: Double) = sqrt(v)
    }
}
