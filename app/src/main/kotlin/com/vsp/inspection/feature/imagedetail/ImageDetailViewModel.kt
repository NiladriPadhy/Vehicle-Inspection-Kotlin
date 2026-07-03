package com.vsp.inspection.feature.imagedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vsp.core.domain.usecase.AddAnnotationUseCase
import com.vsp.core.domain.usecase.AnalyzeImageUseCase
import com.vsp.core.domain.usecase.DeleteAnnotationUseCase
import com.vsp.core.domain.usecase.ObserveAnnotationsUseCase
import com.vsp.core.domain.usecase.ObserveFindingsUseCase
import com.vsp.core.domain.usecase.ObserveImageUseCase
import com.vsp.core.domain.usecase.ObserveImagesUseCase
import com.vsp.core.domain.usecase.ReverifyAnnotationUseCase
import com.vsp.core.domain.usecase.UpdateAnnotationUseCase
import com.vsp.core.model.AIFinding
import com.vsp.core.model.Annotation
import com.vsp.core.model.AnnotationShape
import com.vsp.core.model.AppResult
import com.vsp.core.model.DamageType
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.Severity
import com.vsp.inspection.feature.common.errorMessage
import com.vsp.inspection.feature.common.forChecklistItem
import com.vsp.inspection.feature.common.forChecklistSection
import com.vsp.inspection.feature.common.sortedByCaptureSequence
import com.vsp.inspection.navigation.VspRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ImageDetailUiState(
    val image: InspectionImage? = null,
    val findings: List<AIFinding> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
)

data class ImageDetailEvents(
    val analyzing: Boolean = false,
    val message: String? = null,
    val reverifying: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ImageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeImage: ObserveImageUseCase,
    observeImages: ObserveImagesUseCase,
    observeFindings: ObserveFindingsUseCase,
    observeAnnotations: ObserveAnnotationsUseCase,
    private val analyzeImage: AnalyzeImageUseCase,
    private val addAnnotation: AddAnnotationUseCase,
    private val deleteAnnotation: DeleteAnnotationUseCase,
    private val reverifyAnnotation: ReverifyAnnotationUseCase,
    private val updateAnnotation: UpdateAnnotationUseCase,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<VspRoute.ImageDetail>()
    private val initialImageId: String = route.imageId

    /** When opened from a checklist section/item, the swipe gallery is limited to that scope. */
    private val checklistSectionId: String? = route.checklistSectionId
    private val checklistItemId: String? = route.checklistItemId

    /** Currently displayed image; changes as the user swipes between siblings. */
    private val currentImageId = MutableStateFlow(initialImageId)
    val imageId: String get() = currentImageId.value

    /** Sibling images (same inspection, and same checklist section when scoped), for swiping. */
    val images: StateFlow<List<InspectionImage>> = observeImage(initialImageId)
        .filterNotNull()
        .map { it.inspectionId }
        .distinctUntilChanged()
        .flatMapLatest { observeImages(it) }
        .map { all ->
            val scoped = when {
                checklistItemId != null -> all.forChecklistItem(checklistItemId)
                else -> all.forChecklistSection(checklistSectionId)
            }
            scoped.sortedByCaptureSequence()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<ImageDetailUiState> = combine(
        currentImageId.flatMapLatest { observeImage(it) },
        currentImageId.flatMapLatest { observeFindings(it) },
        currentImageId.flatMapLatest { observeAnnotations(it) },
    ) { image, findings, annotations ->
        ImageDetailUiState(image, findings, annotations)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImageDetailUiState())

    private val _events = MutableStateFlow(ImageDetailEvents())
    val events: StateFlow<ImageDetailEvents> = _events.asStateFlow()

    /** Called by the pager when the user swipes to another image. */
    fun onImageSelected(id: String) {
        if (id != currentImageId.value) {
            currentImageId.value = id
            _events.update { it.copy(message = null) }
        }
    }

    fun analyze() {
        val image = state.value.image ?: return
        if (_events.value.analyzing) return
        _events.update { it.copy(analyzing = true, message = null) }
        viewModelScope.launch {
            val message = when (val result = analyzeImage(image)) {
                is AppResult.Success -> "AI found ${result.value.size} finding(s)."
                is AppResult.Failure -> result.error.errorMessage()
            }
            _events.update { it.copy(analyzing = false, message = message) }
        }
    }

    fun addPin(xNorm: Float, yNorm: Float, damageType: DamageType, severity: Severity, comment: String?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val annotation = Annotation(
                id = UUID.randomUUID().toString(),
                imageId = imageId,
                shape = AnnotationShape.PIN,
                geometryJson = "{\"x\":$xNorm,\"y\":$yNorm}",
                damageType = damageType,
                severity = severity,
                comment = comment,
                createdAt = now,
                updatedAt = now,
            )
            if (addAnnotation(annotation) is AppResult.Failure) {
                _events.update { it.copy(message = "Could not add annotation") }
            }
        }
    }

    fun delete(annotation: Annotation) {
        viewModelScope.launch { deleteAnnotation(annotation.id) }
    }

    /** Persists inspector-entered damage-assessment fields (§13, manual cost). */
    fun updateAssessment(
        annotation: Annotation,
        repairRequired: Boolean?,
        estimatedCost: Double?,
        estimatedSize: String?,
        manualVerified: Boolean,
    ) {
        viewModelScope.launch {
            val updated = annotation.copy(
                repairRequired = repairRequired,
                estimatedCost = estimatedCost,
                estimatedSize = estimatedSize?.ifBlank { null },
                manualVerified = manualVerified,
            )
            if (updateAnnotation(updated) is AppResult.Failure) {
                _events.update { it.copy(message = "Could not save assessment") }
            }
        }
    }

    fun reverify(annotation: Annotation) {
        val image = state.value.image ?: return
        _events.update { it.copy(reverifying = true, message = null) }
        viewModelScope.launch {
            val message = when (val result = reverifyAnnotation(image, annotation)) {
                is AppResult.Success -> if (result.value.confirmed) {
                    "AI confirmed the annotation."
                } else {
                    "AI could not confirm this annotation" +
                        (result.value.correctedDamageType?.let { " (suggests ${it.name})" } ?: "")
                }
                is AppResult.Failure -> result.error.errorMessage()
            }
            _events.update { it.copy(reverifying = false, message = message) }
        }
    }

    fun consumeMessage() = _events.update { it.copy(message = null) }
}
