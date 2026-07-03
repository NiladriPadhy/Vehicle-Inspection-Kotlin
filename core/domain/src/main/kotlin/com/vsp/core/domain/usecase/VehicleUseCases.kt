package com.vsp.core.domain.usecase

import com.vsp.core.domain.repository.VehicleRepository
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.Vehicle
import com.vsp.core.model.VehicleCategory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveVehicleUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) {
    operator fun invoke(vehicleId: String): Flow<Vehicle?> =
        vehicleRepository.observeVehicle(vehicleId)
}

class DecodeVinUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) {
    suspend operator fun invoke(vin: String): AppResult<Vehicle> {
        val normalized = vin.trim().uppercase()
        if (!isValidVin(normalized)) {
            return AppResult.Failure(AppError.Validation("VIN must be 17 valid characters"))
        }
        return vehicleRepository.decodeVin(normalized)
    }

    companion object {
        private val VIN_REGEX = Regex("^[A-HJ-NPR-Z0-9]{17}$")
        fun isValidVin(vin: String): Boolean = VIN_REGEX.matches(vin)
    }
}

class ScanVinFromImageUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) {
    suspend operator fun invoke(imagePath: String): AppResult<String> =
        vehicleRepository.scanVinFromImage(imagePath)
}

class SaveVehicleDetailsUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) {
    suspend operator fun invoke(vehicle: Vehicle): AppResult<Vehicle> {
        if (vehicle.category == VehicleCategory.OLD && vehicle.registrationNumber.isNullOrBlank()) {
            return AppResult.Failure(AppError.Validation("Registration number is required for an Old vehicle"))
        }
        return vehicleRepository.saveVehicle(vehicle)
    }
}

class SaveOldVehicleDetailsUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) {
    suspend operator fun invoke(vehicle: Vehicle): AppResult<Vehicle> {
        if (vehicle.category != VehicleCategory.OLD) {
            return AppResult.Failure(AppError.Validation("Old-vehicle details only apply to Old vehicles"))
        }
        val ownerships = vehicle.numberOfOwnerships
        val keys = vehicle.numberOfKeys
        if (vehicle.registrationNumber.isNullOrBlank()) {
            return AppResult.Failure(AppError.Validation("Registration number is required"))
        }
        if (ownerships == null || ownerships < 0) {
            return AppResult.Failure(AppError.Validation("Number of ownerships is required"))
        }
        if (keys == null || keys < 0) {
            return AppResult.Failure(AppError.Validation("Number of keys is required"))
        }
        return vehicleRepository.saveVehicle(vehicle)
    }
}
