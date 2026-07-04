package com.vsp.inspection.feature.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vsp.core.data.report.ReportDto
import com.vsp.core.domain.usecase.ExportReportPdfUseCase
import com.vsp.core.domain.usecase.GenerateReportUseCase
import com.vsp.core.domain.usecase.GetActiveBrandingUseCase
import com.vsp.core.domain.usecase.ObserveReportUseCase
import com.vsp.core.model.AppResult
import com.vsp.core.model.RepairRecommendation
import com.vsp.core.model.Report
import com.vsp.core.model.Valuation
import com.vsp.core.model.config.BrandingConfig
import com.vsp.inspection.BuildConfig
import com.vsp.inspection.feature.common.errorMessage
import com.vsp.inspection.navigation.VspRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class ReportUiState(
    val generating: Boolean = false,
    val exportingPdf: Boolean = false,
    val pdfPath: String? = null,
    val message: String? = null,
)

/** A single label/value row for the vehicle detail and "at a glance" cards. */
data class DetailRow(val label: String, val value: String)

/** A category rating for the inspection summary (mirrors the PDF summary cards). */
data class CategoryRating(val label: String, val rating: Int)

/** Structured, display-ready report content parsed from the generated report JSON. */
data class ReportContent(
    val vehicleTitle: String,
    val subtitle: String,
    val vehicleDetails: List<DetailRow>,
    val glanceDetails: List<DetailRow>,
    val overallRating: Int?,
    val categoryRatings: List<CategoryRating>,
    val recommendation: String?,
    val valuation: Valuation?,
    val branding: BrandingConfig,
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeReport: ObserveReportUseCase,
    private val generateReport: GenerateReportUseCase,
    private val exportReportPdf: ExportReportPdfUseCase,
    private val getActiveBranding: GetActiveBrandingUseCase,
) : ViewModel() {

    val inspectionId: String = savedStateHandle.toRoute<VspRoute.Report>().inspectionId

    val report: StateFlow<Report?> =
        observeReport(inspectionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val branding = flow { emit(getActiveBranding()) }

    val content: StateFlow<ReportContent?> =
        combine(report, branding) { report, branding -> report?.let { buildContent(it, branding) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    init {
        // Always regenerate on entry so edits made after a prior generation are reflected.
        generate()
    }

    fun generate() {
        if (_state.value.generating) return
        _state.update { it.copy(generating = true, message = null) }
        viewModelScope.launch {
            val message = when (val result = generateReport(inspectionId)) {
                is AppResult.Success -> null
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

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ---- Report JSON → display content --------------------------------------

    private fun buildContent(report: Report, branding: BrandingConfig): ReportContent? {
        val dto = runCatching { json.decodeFromString(ReportDto.serializer(), report.json) }.getOrNull()
            ?: return null
        val v = dto.vehicle
        val company = companyName(branding)

        val subtitle = listOfNotNull(
            v.variant ?: v.trim,
            v.year?.toString(),
            v.transmission,
            v.fuelType,
        ).joinToString("  •  ")

        val vehicleDetails = buildList {
            v.make?.let { add(DetailRow("Make", it)) }
            v.model?.let { add(DetailRow("Model", it)) }
            (v.variant ?: v.trim)?.let { add(DetailRow("Variant", it)) }
            v.year?.let { add(DetailRow("Year", it.toString())) }
            v.bodyStyle?.let { add(DetailRow("Body style", it)) }
            v.fuelType?.let { add(DetailRow("Fuel", it)) }
            v.transmission?.let { add(DetailRow("Transmission", it)) }
            v.color?.let { add(DetailRow("Color", it)) }
            v.vin?.let { add(DetailRow("VIN", it)) }
            v.registrationNumber?.let { add(DetailRow("Registration", it)) }
            v.engineNumber?.let { add(DetailRow("Engine number", it)) }
            v.chassisNumber?.let { add(DetailRow("Chassis number", it)) }
            v.odometerKm?.let { add(DetailRow("Odometer", "$it km")) }
            v.numberOfOwnerships?.let { add(DetailRow("Ownerships", it.toString())) }
            v.numberOfKeys?.let { add(DetailRow("Keys", it.toString())) }
            add(DetailRow("Category", v.category))
        }

        val glanceDetails = buildList {
            company?.let { add(DetailRow("Company Name", it)) }
            add(DetailRow("Inspection date", formatDate(report.generatedAt)))
            v.vin?.let { add(DetailRow("VIN", it)) }
            v.chassisNumber?.let { add(DetailRow("Chassis number", it)) }
            v.engineNumber?.let { add(DetailRow("Engine number", it)) }
            v.registrationNumber?.let { add(DetailRow("Registration", it)) }
            v.color?.let { add(DetailRow("Color", it)) }
            v.odometerKm?.let { add(DetailRow("Odometer", "$it km")) }
            v.numberOfOwnerships?.let { add(DetailRow("Ownerships", it.toString())) }
            v.numberOfKeys?.let { add(DetailRow("Keys", it.toString())) }
            add(DetailRow("Category", v.category))
        }

        val recommendation = dto.finalAssessment?.recommendation
            ?.takeIf { it.isNotBlank() }
            ?.let(::recommendationLabel)
            ?: dto.finalRecommendation.takeIf { it.isNotBlank() }?.let(::recommendationLabel)

        return ReportContent(
            vehicleTitle = vehicleTitle(dto),
            subtitle = subtitle,
            vehicleDetails = vehicleDetails,
            glanceDetails = glanceDetails,
            overallRating = overallRating(dto),
            categoryRatings = dto.finalAssessment?.categoryRatings
                ?.map { (label, rating) -> CategoryRating(label, rating.coerceIn(1, 5)) }
                .orEmpty(),
            recommendation = recommendation,
            valuation = dto.valuation,
            branding = branding,
        )
    }

    private fun vehicleTitle(dto: ReportDto): String {
        val v = dto.vehicle
        return listOfNotNull(v.make, v.model).joinToString(" ").ifBlank {
            v.vin ?: v.registrationNumber ?: "Vehicle Inspection"
        }.uppercase(Locale.getDefault())
    }

    private fun overallRating(dto: ReportDto): Int? {
        val ratings = dto.finalAssessment?.categoryRatings?.values?.toList().orEmpty()
        if (ratings.isNotEmpty()) return ratings.average().roundToInt().coerceIn(1, 5)
        var perfect = 0
        var imperfect = 0
        dto.checklist.forEach { section ->
            section.items.forEach { item ->
                when (verdict(item.status)) {
                    1 -> perfect++
                    -1 -> imperfect++
                }
            }
        }
        val total = perfect + imperfect
        if (total == 0) return null
        return (perfect.toDouble() / total * 5).roundToInt().coerceIn(1, 5)
    }

    private fun verdict(status: String?): Int = when (status) {
        "OK", "YES", "PASS", "GOOD" -> 1
        "NOT_OK", "NO", "FAIL", "MINOR_SCRATCHES", "MAJOR_SCRATCHES", "DAMAGE" -> -1
        else -> 0
    }

    private fun recommendationLabel(value: String): String =
        RepairRecommendation.entries.firstOrNull { it.name == value }?.label ?: value

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

    private fun companyName(branding: BrandingConfig): String? {
        branding.companyName.takeIf { it.isNotBlank() }?.let { return it }
        return BuildConfig.VENDOR_ID
            .takeIf { it.isNotBlank() && !it.equals("default", ignoreCase = true) }
            ?.split('-', '_', ' ')
            ?.filter { it.isNotBlank() }
            ?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
