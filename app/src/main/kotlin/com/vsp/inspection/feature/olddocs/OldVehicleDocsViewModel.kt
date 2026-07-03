package com.vsp.inspection.feature.olddocs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vsp.core.domain.usecase.CaptureImageUseCase
import com.vsp.core.domain.usecase.ObserveImagesUseCase
import com.vsp.core.domain.usecase.ObserveVehicleUseCase
import com.vsp.core.domain.usecase.ResumeInspectionUseCase
import com.vsp.core.domain.usecase.SaveOldVehicleDetailsUseCase
import com.vsp.core.model.AppResult
import com.vsp.core.model.CaptureState
import com.vsp.core.model.Section
import com.vsp.core.model.Vehicle
import com.vsp.core.model.catalog.DocumentCatalog
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

data class OldDocsUiState(
    val loaded: Boolean = false,
    val slots: List<DocumentCatalog.DocumentSlot> = DocumentCatalog.oldVehicleDocuments,
    val capturedTypes: Set<String> = emptySet(),
    val numberOfOwnerships: String = "",
    val numberOfKeys: String = "",
    val isBusy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
) {
    val currentSlot: DocumentCatalog.DocumentSlot?
        get() = slots.firstOrNull { it.type.name !in capturedTypes }
    val allDocsCaptured: Boolean get() = currentSlot == null
    val canSubmit: Boolean
        get() = allDocsCaptured && numberOfOwnerships.isNotBlank() && numberOfKeys.isNotBlank()
}

@HiltViewModel
class OldVehicleDocsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resumeInspection: ResumeInspectionUseCase,
    private val observeVehicle: ObserveVehicleUseCase,
    private val observeImages: ObserveImagesUseCase,
    private val captureImage: CaptureImageUseCase,
    private val saveOldDetails: SaveOldVehicleDetailsUseCase,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<VspRoute.OldVehicleDocs>()
    val inspectionId: String = route.inspectionId
    private var vehicleId: String? = null

    private val _state = MutableStateFlow(OldDocsUiState())
    val state: StateFlow<OldDocsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val inspection = resumeInspection(inspectionId).first()
            vehicleId = inspection?.vehicleId
            val vehicle = inspection?.let { observeVehicle(it.vehicleId).first() }
            _state.update {
                it.copy(
                    loaded = true,
                    numberOfOwnerships = vehicle?.numberOfOwnerships?.toString().orEmpty(),
                    numberOfKeys = vehicle?.numberOfKeys?.toString().orEmpty(),
                )
            }
        }
        viewModelScope.launch {
            observeImages(inspectionId).collect { images ->
                val captured = images
                    .filter { it.section == Section.DOCUMENT && it.captureState == CaptureState.CAPTURED }
                    .mapNotNull { it.documentType?.name ?: it.position }
                    .toSet()
                _state.update { it.copy(capturedTypes = captured) }
            }
        }
    }

    fun onOwnershipsChange(v: String) = _state.update { it.copy(numberOfOwnerships = v.filter(Char::isDigit)) }
    fun onKeysChange(v: String) = _state.update { it.copy(numberOfKeys = v.filter(Char::isDigit)) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun captureDocument(rawImagePath: String) {
        val slot = _state.value.currentSlot ?: return
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = captureImage(inspectionId, Section.DOCUMENT, slot.type.name, rawImagePath)) {
                is AppResult.Success -> _state.update { it.copy(isBusy = false) }
                is AppResult.Failure -> _state.update { it.copy(isBusy = false, error = result.error.errorMessage()) }
            }
        }
    }

    fun submit() {
        val current = _state.value
        val id = vehicleId ?: return
        if (!current.canSubmit || current.isBusy) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val existing = observeVehicle(id).first() ?: Vehicle(id = id)
            val updated = existing.copy(
                numberOfOwnerships = current.numberOfOwnerships.toIntOrNull(),
                numberOfKeys = current.numberOfKeys.toIntOrNull(),
            )
            when (val result = saveOldDetails(updated)) {
                is AppResult.Success -> _state.update { it.copy(isBusy = false, done = true) }
                is AppResult.Failure -> _state.update { it.copy(isBusy = false, error = result.error.errorMessage()) }
            }
        }
    }
}
