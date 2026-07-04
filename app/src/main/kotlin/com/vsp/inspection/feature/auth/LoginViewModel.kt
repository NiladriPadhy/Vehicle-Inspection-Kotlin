package com.vsp.inspection.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vsp.core.domain.usecase.SignInUseCase
import com.vsp.core.domain.usecase.SyncConfigUseCase
import com.vsp.core.model.AppResult
import com.vsp.inspection.feature.common.errorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signIn: SignInUseCase,
    private val syncConfig: SyncConfigUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (current.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = signIn(current.email, current.password)) {
                is AppResult.Success -> {
                    // Login boundary: pull/seed the vendor questionnaire before entering the app.
                    runCatching { syncConfig() }
                    _state.update { it.copy(isLoading = false, signedIn = true) }
                }
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, error = result.error.errorMessage()) }
            }
        }
    }
}
