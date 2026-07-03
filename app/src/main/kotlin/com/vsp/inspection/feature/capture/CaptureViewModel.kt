package com.vsp.inspection.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vsp.core.domain.usecase.CaptureImageUseCase
import com.vsp.core.domain.usecase.ObserveImagesUseCase
import com.vsp.core.domain.usecase.SkipPositionUseCase
import com.vsp.core.model.AppResult
import com.vsp.core.model.CaptureState
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.Section
import com.vsp.core.model.catalog.CapturePosition
import com.vsp.core.model.catalog.PositionCatalog
import com.vsp.inspection.feature.common.errorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CaptureUiState(
    val section: Section = Section.EXTERIOR,
    val positions: List<CapturePosition> = emptyList(),
    val currentIndex: Int = 0,
    val addressedCount: Int = 0,
    val isBusy: Boolean = false,
    val error: String? = null,
    val sectionComplete: Boolean = false,
) {
    val total: Int get() = positions.size
    val current: CapturePosition? get() = positions.getOrNull(currentIndex)
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val observeImages: ObserveImagesUseCase,
    private val captureImage: CaptureImageUseCase,
    private val skipPosition: SkipPositionUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var initialized = false
    private lateinit var inspectionId: String

    fun initialize(inspectionId: String, section: Section) {
        if (initialized) return
        initialized = true
        this.inspectionId = inspectionId
        _state.update { it.copy(section = section, positions = PositionCatalog.forSection(section)) }
        viewModelScope.launch {
            observeImages(inspectionId).collect { images -> recomputeProgress(images) }
        }
    }

    /** Enforces strict sequence order: current position = first not-yet-addressed slot. */
    private fun recomputeProgress(images: List<InspectionImage>) {
        val section = _state.value.section
        val positions = _state.value.positions
        val addressed = images
            .filter { it.section == section && it.isAddressed() }
            .map { it.position }
            .toSet()
        val nextIndex = positions.indexOfFirst { it.id !in addressed }
        _state.update {
            it.copy(
                addressedCount = addressed.count { pos -> positions.any { p -> p.id == pos } },
                currentIndex = if (nextIndex == -1) positions.size else nextIndex,
                sectionComplete = nextIndex == -1,
            )
        }
    }

    fun capture(rawImagePath: String) {
        val position = _state.value.current ?: return
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = captureImage(inspectionId, _state.value.section, position.id, rawImagePath)) {
                is AppResult.Success -> _state.update { it.copy(isBusy = false) }
                is AppResult.Failure ->
                    _state.update { it.copy(isBusy = false, error = result.error.errorMessage()) }
            }
        }
    }

    fun skip(reason: String) {
        val position = _state.value.current ?: return
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = skipPosition(inspectionId, _state.value.section, position.id, reason)) {
                is AppResult.Success -> _state.update { it.copy(isBusy = false) }
                is AppResult.Failure ->
                    _state.update { it.copy(isBusy = false, error = result.error.errorMessage()) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun InspectionImage.isAddressed(): Boolean = when (captureState) {
        CaptureState.CAPTURED -> true
        CaptureState.SKIPPED -> !skipReason.isNullOrBlank()
        CaptureState.PENDING -> false
    }
}
