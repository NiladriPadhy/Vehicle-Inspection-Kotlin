package com.vsp.core.domain.usecase

import com.vsp.core.domain.repository.AuthRepository
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.Session
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): AppResult<Session> {
        if (email.isBlank() || password.isBlank()) {
            return AppResult.Failure(AppError.Validation("Email and password are required"))
        }
        return authRepository.signIn(email.trim(), password)
    }
}

class SignUpUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(displayName: String, email: String, password: String): AppResult<Session> {
        if (displayName.isBlank()) return AppResult.Failure(AppError.Validation("Name is required"))
        if (email.isBlank() || !email.contains('@')) {
            return AppResult.Failure(AppError.Validation("A valid email is required"))
        }
        if (password.length < 6) {
            return AppResult.Failure(AppError.Validation("Password must be at least 6 characters"))
        }
        return authRepository.signUp(displayName.trim(), email.trim(), password)
    }
}

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = authRepository.signOut()
}

class ObserveSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke() = authRepository.session
}
