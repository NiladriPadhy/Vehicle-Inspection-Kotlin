package com.vsp.inspection.feature.verification

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vsp.core.domain.usecase.FinalizeInspectionUseCase
import com.vsp.core.domain.usecase.ResumeInspectionUseCase
import com.vsp.core.domain.usecase.RunFinalVerificationUseCase
import com.vsp.core.model.AppResult
import com.vsp.core.model.FinalVerification
import com.vsp.inspection.feature.common.errorMessage
import com.vsp.inspection.navigation.VspRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerificationUiState(
    val running: Boolean = false,
    val finalizing: Boolean = false,
    val verification: FinalVerification? = null,
    val message: String? = null,
    val finalized: Boolean = false,
)

@HiltViewModel
class VerificationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resumeInspection: ResumeInspectionUseCase,
    private val runFinalVerification: RunFinalVerificationUseCase,
    private val finalizeInspection: FinalizeInspectionUseCase,
) : ViewModel() {

    val inspectionId: String = savedStateHandle.toRoute<VspRoute.FinalVerification>().inspectionId

    private val _state = MutableStateFlow(VerificationUiState())
    val state: StateFlow<VerificationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val inspection = resumeInspection(inspectionId).first()
            if (inspection?.confidenceScore != null) {
                _state.update { it.copy(message = "Previously scored: ${inspection.overallCondition ?: ""}") }
            }
        }
    }

    fun runVerification() {
        if (_state.value.running) return
        _state.update { it.copy(running = true, message = null) }
        viewModelScope.launch {
            when (val result = runFinalVerification(inspectionId)) {
                is AppResult.Success ->
                    _state.update { it.copy(running = false, verification = result.value) }
                is AppResult.Failure ->
                    _state.update {
                        it.copy(
                            running = false,
                            message = result.error.errorMessage() +
                                " You can still finalize the inspection manually.",
                        )
                    }
            }
        }
    }

    fun finalize() {
        if (_state.value.finalizing) return
        _state.update { it.copy(finalizing = true, message = null) }
        viewModelScope.launch {
            when (val result = finalizeInspection(inspectionId)) {
                is AppResult.Success -> _state.update { it.copy(finalizing = false, finalized = true) }
                is AppResult.Failure ->
                    _state.update { it.copy(finalizing = false, message = result.error.errorMessage()) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
