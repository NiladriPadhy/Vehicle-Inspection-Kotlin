package com.vsp.inspection.feature.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vsp.core.domain.usecase.ExportDataUseCase
import com.vsp.core.domain.usecase.ImportDataUseCase
import com.vsp.core.domain.usecase.ObserveSessionUseCase
import com.vsp.core.model.AppResult
import com.vsp.core.model.Session
import com.vsp.inspection.feature.common.errorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DataUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val exportedZipPath: String? = null,
)

@HiltViewModel
class DataViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    private val exportData: ExportDataUseCase,
    private val importData: ImportDataUseCase,
) : ViewModel() {

    private val inspectorId: StateFlow<String?> =
        observeSession().map { it?.inspectorId }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    fun export() {
        val id = inspectorId.value ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null, error = null, exportedZipPath = null) }
        viewModelScope.launch {
            when (val result = exportData(id)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        busy = false,
                        exportedZipPath = result.value.filePath,
                        message = "Exported ${result.value.inspectionCount} inspection(s) and ${result.value.imageCount} photo(s).",
                    )
                }
                is AppResult.Failure -> _state.update { it.copy(busy = false, error = result.error.errorMessage()) }
            }
        }
    }

    fun import(zipPath: String) {
        val id = inspectorId.value ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null, error = null, exportedZipPath = null) }
        viewModelScope.launch {
            when (val result = importData(zipPath, id)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        busy = false,
                        message = "Imported ${result.value.imported} inspection(s), skipped ${result.value.skipped}, ${result.value.imageCount} photo(s).",
                    )
                }
                is AppResult.Failure -> _state.update { it.copy(busy = false, error = result.error.errorMessage()) }
            }
        }
    }

    fun consumeExportPath() = _state.update { it.copy(exportedZipPath = null) }
}
