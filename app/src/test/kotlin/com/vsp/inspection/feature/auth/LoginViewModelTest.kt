package com.vsp.inspection.feature.auth

import com.google.common.truth.Truth.assertThat
import com.vsp.core.domain.repository.AuthRepository
import com.vsp.core.domain.repository.ConfigRepository
import com.vsp.core.domain.usecase.SignInUseCase
import com.vsp.core.domain.usecase.SyncConfigUseCase
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.Session
import com.vsp.core.model.config.BaselineQuestionnaire
import com.vsp.core.model.config.QuestionnaireConfig
import com.vsp.core.model.config.VehicleCatalog
import com.vsp.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private class FakeAuthRepository(private val succeed: Boolean) : AuthRepository {
    override val session: Flow<Session?> = flowOf(null)
    override suspend fun signIn(email: String, password: String): AppResult<Session> =
        if (succeed) AppResult.Success(Session("id", "Name", email, 0L))
        else AppResult.Failure(AppError.Auth())
    override suspend fun signUp(displayName: String, email: String, password: String): AppResult<Session> =
        if (succeed) AppResult.Success(Session("id", displayName, email, 0L))
        else AppResult.Failure(AppError.Auth())
    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)
    override fun hasValidOfflineSession(): Boolean = false
}

private class FakeConfigRepository : ConfigRepository {
    override suspend fun activeQuestionnaire(): QuestionnaireConfig = BaselineQuestionnaire.build()
    override suspend fun syncOnLogin(): AppResult<QuestionnaireConfig> = AppResult.Success(BaselineQuestionnaire.build())
    override suspend fun vehicleCatalog(): VehicleCatalog? = null
}

private fun syncConfig() = SyncConfigUseCase(FakeConfigRepository())

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `successful sign-in sets signedIn`() = runTest {
        val vm = LoginViewModel(SignInUseCase(FakeAuthRepository(succeed = true)), syncConfig())
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("secret")
        vm.submit()
        advanceUntilIdle()
        assertThat(vm.state.value.signedIn).isTrue()
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun `failed sign-in surfaces error`() = runTest {
        val vm = LoginViewModel(SignInUseCase(FakeAuthRepository(succeed = false)), syncConfig())
        vm.onEmailChange("a@b.com")
        vm.onPasswordChange("secret")
        vm.submit()
        advanceUntilIdle()
        assertThat(vm.state.value.signedIn).isFalse()
        assertThat(vm.state.value.error).isNotNull()
    }

    @Test
    fun `blank credentials fail validation`() = runTest {
        val vm = LoginViewModel(SignInUseCase(FakeAuthRepository(succeed = true)), syncConfig())
        vm.onEmailChange("")
        vm.onPasswordChange("")
        vm.submit()
        advanceUntilIdle()
        assertThat(vm.state.value.signedIn).isFalse()
    }
}
