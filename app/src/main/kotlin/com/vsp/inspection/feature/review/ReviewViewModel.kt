package com.vsp.inspection.feature.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vsp.core.domain.usecase.GetInspectionCompletenessUseCase
import com.vsp.core.domain.usecase.ObserveImagesUseCase
import com.vsp.core.model.AppResult
import com.vsp.core.model.Completeness
import com.vsp.core.model.InspectionImage
import com.vsp.inspection.feature.common.sortedByCaptureSequence
import com.vsp.inspection.navigation.VspRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val images: List<InspectionImage> = emptyList(),
    val completeness: Completeness? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeImages: ObserveImagesUseCase,
    private val getCompleteness: GetInspectionCompletenessUseCase,
) : ViewModel() {

    val inspectionId: String = savedStateHandle.toRoute<VspRoute.Review>().inspectionId

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeImages(inspectionId).collect { images ->
                val completeness = (getCompleteness(inspectionId) as? AppResult.Success)?.value
                _state.update {
                    it.copy(
                        images = images.sortedByCaptureSequence(),
                        completeness = completeness,
                        loading = false,
                    )
                }
            }
        }
    }
}
