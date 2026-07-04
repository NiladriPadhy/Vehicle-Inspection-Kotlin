package com.vsp.inspection.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vsp.core.domain.usecase.SignUpUseCase
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

data class SignUpUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val signUp: SignUpUseCase,
    private val syncConfig: SyncConfigUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(displayName = value, error = null) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onConfirmChange(value: String) = _state.update { it.copy(confirmPassword = value, error = null) }

    fun submit() {
        val current = _state.value
        if (current.isLoading) return
        if (current.password != current.confirmPassword) {
            _state.update { it.copy(error = "Passwords do not match") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = signUp(current.displayName, current.email, current.password)) {
                is AppResult.Success -> {
                    runCatching { syncConfig() }
                    _state.update { it.copy(isLoading = false, signedIn = true) }
                }
                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, error = result.error.errorMessage()) }
            }
        }
    }
}
