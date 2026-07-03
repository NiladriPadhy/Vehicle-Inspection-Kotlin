package com.vsp.inspection.feature.common

import com.vsp.core.model.AppError
import com.vsp.core.model.ImageQuality

/** Maps a typed [AppError] to a user-facing message. Centralized so screens stay declarative. */
fun AppError.errorMessage(): String = when (this) {
    is AppError.Network -> if (retryable) "Network problem. Check your connection and retry." else "Network unavailable."
    is AppError.Auth -> "Sign-in failed. Check your credentials."
    is AppError.Permission -> "Permission required: $type."
    is AppError.ImageQualityError -> when (reason) {
        ImageQuality.BLURRY -> "Image is blurry — hold steady and retake."
        ImageQuality.DARK -> "Image is too dark — add light and retake."
        ImageQuality.OVEREXPOSED -> "Image is overexposed — reduce glare and retake."
        ImageQuality.INCOMPLETE -> "Image is incomplete — reframe and retake."
        ImageQuality.OK -> "Image quality issue — please retake."
    }
    is AppError.AiUnavailable -> "AI analysis is unavailable right now. You can continue and analyze later."
    is AppError.AiInvalidResponse -> "AI returned an unexpected result. Please retry."
    is AppError.VinLookupFailed -> "Could not decode the VIN. Enter details manually."
    is AppError.Storage -> "Storage error: $reason."
    is AppError.Validation -> detail
    is AppError.Unknown -> "Something went wrong. Please try again."
}
