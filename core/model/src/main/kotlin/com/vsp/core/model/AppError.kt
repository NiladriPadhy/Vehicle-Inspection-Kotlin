package com.vsp.core.model

/** Typed, cross-layer error model. Use cases surface these instead of throwing. */
sealed interface AppError {
    val cause: Throwable?

    data class Network(val retryable: Boolean = true, override val cause: Throwable? = null) : AppError
    data class Auth(override val cause: Throwable? = null) : AppError
    data class Permission(val type: String, override val cause: Throwable? = null) : AppError
    data class ImageQualityError(val reason: ImageQuality, override val cause: Throwable? = null) : AppError
    data class AiUnavailable(override val cause: Throwable? = null) : AppError
    data class AiInvalidResponse(val detail: String, override val cause: Throwable? = null) : AppError
    data class VinLookupFailed(override val cause: Throwable? = null) : AppError
    data class Storage(val reason: String, override val cause: Throwable? = null) : AppError
    data class Validation(val detail: String, override val cause: Throwable? = null) : AppError
    data class Unknown(override val cause: Throwable? = null) : AppError
}
