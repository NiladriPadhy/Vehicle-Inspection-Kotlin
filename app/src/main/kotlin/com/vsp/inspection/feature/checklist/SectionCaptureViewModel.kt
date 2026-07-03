package com.vsp.inspection.feature.checklist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vsp.core.domain.usecase.CaptureSectionImageUseCase
import com.vsp.core.domain.usecase.DeleteImageUseCase
import com.vsp.core.domain.usecase.ObserveImagesUseCase
import com.vsp.core.model.AppResult
import com.vsp.core.model.CaptureState
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.Section
import com.vsp.inspection.BuildConfig
import com.vsp.inspection.feature.common.errorMessage
import com.vsp.inspection.navigation.VspRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SectionCaptureUiState(
    val isBusy: Boolean = false,
    val error: String? = null,
    val max: Int = BuildConfig.MAX_IMAGES_PER_ITEM,
    /** Total captured photos for this item/section (includes photos from this session). */
    val totalCount: Int = 0,
    /** Ids of photos captured during the current visit; used for the discard action. */
    val sessionIds: List<String> = emptyList(),
    val showDiscardDialog: Boolean = false,
    val done: Boolean = false,
) {
    val sessionCount: Int get() = sessionIds.size
    val remaining: Int get() = (max - totalCount).coerceAtLeast(0)
    val limitReached: Boolean get() = totalCount >= max
}

@HiltViewModel
class SectionCaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeImages: ObserveImagesUseCase,
    private val captureSectionImage: CaptureSectionImageUseCase,
    private val deleteImage: DeleteImageUseCase,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<VspRoute.SectionCapture>()
    private val inspectionId = route.inspectionId
    private val checklistSectionId = route.sectionId
    private val checklistItemId = route.itemId
    private val section = runCatching { Section.valueOf(route.section) }.getOrDefault(Section.EXTERIOR)

    private val _state = MutableStateFlow(SectionCaptureUiState())
    val state: StateFlow<SectionCaptureUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeImages(inspectionId).collect { images ->
                val count = images.count { it.captureState == CaptureState.CAPTURED && belongsHere(it) }
                _state.update { it.copy(totalCount = count) }
            }
        }
    }

    fun capture(rawImagePath: String) {
        if (_state.value.isBusy) return
        if (_state.value.limitReached) {
            _state.update { it.copy(error = "Limit reached — up to ${it.max} photos allowed here.") }
            return
        }
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = captureSectionImage(inspectionId, section, checklistSectionId, checklistItemId, rawImagePath)) {
                is AppResult.Success ->
                    _state.update { it.copy(isBusy = false, sessionIds = it.sessionIds + result.value.id) }
                is AppResult.Failure ->
                    _state.update { it.copy(isBusy = false, error = result.error.errorMessage()) }
            }
        }
    }

    /** Invoked from the toolbar/system back: confirm if there are unsaved-session photos. */
    fun onBackRequested() {
        if (_state.value.sessionCount > 0) {
            _state.update { it.copy(showDiscardDialog = true) }
        } else {
            _state.update { it.copy(done = true) }
        }
    }

    fun keepAndExit() {
        _state.update { it.copy(showDiscardDialog = false, done = true) }
    }

    fun discardAndExit() {
        val ids = _state.value.sessionIds
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            ids.forEach { deleteImage(it) }
            _state.update { it.copy(isBusy = false, showDiscardDialog = false, sessionIds = emptyList(), done = true) }
        }
    }

    fun dismissDiscardDialog() = _state.update { it.copy(showDiscardDialog = false) }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun belongsHere(image: InspectionImage): Boolean =
        if (checklistItemId != null) {
            image.checklistItemId == checklistItemId
        } else {
            image.checklistItemId == null && image.checklistSectionId == checklistSectionId
        }
}
