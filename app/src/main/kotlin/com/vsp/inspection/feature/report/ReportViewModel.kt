package com.vsp.inspection.feature.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vsp.core.domain.usecase.ExportReportPdfUseCase
import com.vsp.core.domain.usecase.GenerateReportUseCase
import com.vsp.core.domain.usecase.ObserveReportUseCase
import com.vsp.core.domain.usecase.ObserveSyncStatusUseCase
import com.vsp.core.domain.usecase.RetryFailedSyncUseCase
import com.vsp.core.model.AppResult
import com.vsp.core.model.Report
import com.vsp.core.model.SyncSummary
import com.vsp.inspection.feature.common.errorMessage
import com.vsp.inspection.navigation.VspRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportUiState(
    val generating: Boolean = false,
    val exportingPdf: Boolean = false,
    val pdfPath: String? = null,
    val message: String? = null,
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeReport: ObserveReportUseCase,
    observeSyncStatus: ObserveSyncStatusUseCase,
    private val generateReport: GenerateReportUseCase,
    private val exportReportPdf: ExportReportPdfUseCase,
    private val retryFailedSync: RetryFailedSyncUseCase,
) : ViewModel() {

    val inspectionId: String = savedStateHandle.toRoute<VspRoute.Report>().inspectionId

    val report: StateFlow<Report?> =
        observeReport(inspectionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val syncStatus: StateFlow<SyncSummary> =
        observeSyncStatus(inspectionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncSummary(0, 0, 0, 0))

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    init {
        if (report.value == null) generate()
    }

    fun generate() {
        if (_state.value.generating) return
        _state.update { it.copy(generating = true, message = null) }
        viewModelScope.launch {
            val message = when (val result = generateReport(inspectionId)) {
                is AppResult.Success -> "Report generated."
                is AppResult.Failure -> result.error.errorMessage()
            }
            _state.update { it.copy(generating = false, message = message) }
        }
    }

    fun exportPdf() {
        if (_state.value.exportingPdf) return
        _state.update { it.copy(exportingPdf = true, message = null) }
        viewModelScope.launch {
            when (val result = exportReportPdf(inspectionId)) {
                is AppResult.Success ->
                    _state.update { it.copy(exportingPdf = false, pdfPath = result.value) }
                is AppResult.Failure ->
                    _state.update { it.copy(exportingPdf = false, message = result.error.errorMessage()) }
            }
        }
    }

    fun consumePdfPath() = _state.update { it.copy(pdfPath = null) }

    fun retrySync() {
        viewModelScope.launch { retryFailedSync(inspectionId) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
