package com.vsp.inspection.feature.identify

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vsp.core.domain.usecase.DecodeVinUseCase
import com.vsp.core.domain.usecase.ObserveVehicleUseCase
import com.vsp.core.domain.usecase.ResumeInspectionUseCase
import com.vsp.core.domain.usecase.SaveVehicleDetailsUseCase
import com.vsp.core.domain.usecase.ScanVinFromImageUseCase
import com.vsp.core.model.AppResult
import com.vsp.core.model.Vehicle
import com.vsp.core.model.VehicleCategory
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

data class IdentifyUiState(
    val loaded: Boolean = false,
    val vin: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val color: String = "",
    val odometer: String = "",
    val category: VehicleCategory = VehicleCategory.NEW,
    val scanning: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class IdentifyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resumeInspection: ResumeInspectionUseCase,
    private val observeVehicle: ObserveVehicleUseCase,
    private val saveVehicle: SaveVehicleDetailsUseCase,
    private val scanVin: ScanVinFromImageUseCase,
    private val decodeVin: DecodeVinUseCase,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<VspRoute.IdentifyVehicle>()
    val inspectionId: String = route.inspectionId
    private var vehicleId: String? = null

    private val _state = MutableStateFlow(IdentifyUiState())
    val state: StateFlow<IdentifyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val inspection = resumeInspection(inspectionId).first() ?: return@launch
            vehicleId = inspection.vehicleId
            val vehicle = observeVehicle(inspection.vehicleId).first()
            _state.update {
                it.copy(
                    loaded = true,
                    vin = vehicle?.vin.orEmpty(),
                    make = vehicle?.make.orEmpty(),
                    model = vehicle?.model.orEmpty(),
                    year = vehicle?.year?.toString().orEmpty(),
                    color = vehicle?.color.orEmpty(),
                    odometer = vehicle?.odometerKm?.toString().orEmpty(),
                    category = inspection.vehicleCategory,
                )
            }
        }
    }

    fun onVinChange(v: String) = _state.update { it.copy(vin = v.uppercase(), error = null) }
    fun onMakeChange(v: String) = _state.update { it.copy(make = v) }
    fun onModelChange(v: String) = _state.update { it.copy(model = v) }
    fun onYearChange(v: String) = _state.update { it.copy(year = v.filter(Char::isDigit)) }
    fun onColorChange(v: String) = _state.update { it.copy(color = v) }
    fun onOdometerChange(v: String) = _state.update { it.copy(odometer = v.filter(Char::isDigit)) }

    fun onVinScanned(imagePath: String) {
        _state.update { it.copy(scanning = true, error = null) }
        viewModelScope.launch {
            when (val scan = scanVin(imagePath)) {
                is AppResult.Success -> {
                    val vin = scan.value
                    val decoded = (decodeVin(vin) as? AppResult.Success)?.value
                    _state.update {
                        it.copy(
                            scanning = false,
                            vin = vin,
                            make = decoded?.manufacturer ?: it.make,
                            year = decoded?.year?.toString() ?: it.year,
                        )
                    }
                }
                is AppResult.Failure -> _state.update { it.copy(scanning = false, error = scan.error.errorMessage()) }
            }
        }
    }

    fun save() {
        val current = _state.value
        val id = vehicleId ?: return
        if (current.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val existing = observeVehicle(id).first() ?: Vehicle(id = id)
            val updated = existing.copy(
                vin = current.vin.ifBlank { null },
                make = current.make.ifBlank { null },
                model = current.model.ifBlank { null },
                year = current.year.toIntOrNull(),
                color = current.color.ifBlank { null },
                odometerKm = current.odometer.toIntOrNull(),
                category = current.category,
            )
            when (val result = saveVehicle(updated)) {
                is AppResult.Success -> _state.update { it.copy(saving = false, done = true) }
                is AppResult.Failure -> _state.update { it.copy(saving = false, error = result.error.errorMessage()) }
            }
        }
    }
}
