package com.vsp.core.data.repository

import com.vsp.core.data.local.dao.VehicleDao
import com.vsp.core.data.mapper.toDomain
import com.vsp.core.data.mapper.toEntity
import com.vsp.core.data.vin.VinOcrScanner
import com.vsp.core.domain.coroutine.DispatcherProvider
import com.vsp.core.domain.port.VinDecodeSource
import com.vsp.core.domain.repository.VehicleRepository
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val vinDecodeSource: VinDecodeSource,
    private val vinOcrScanner: VinOcrScanner,
    private val dispatchers: DispatcherProvider,
) : VehicleRepository {

    override fun observeVehicle(id: String): Flow<Vehicle?> =
        vehicleDao.observe(id).map { it?.toDomain() }

    override suspend fun decodeVin(vin: String): AppResult<Vehicle> = withContext(dispatchers.io) {
        val decoded = vinDecodeSource.decode(vin)
            ?: return@withContext AppResult.Failure(AppError.VinLookupFailed())
        AppResult.Success(decoded)
    }

    override suspend fun scanVinFromImage(imagePath: String): AppResult<String> =
        withContext(dispatchers.io) {
            runCatching { vinOcrScanner.scan(imagePath) }
                .fold(
                    onSuccess = { vin ->
                        if (vin != null) AppResult.Success(vin)
                        else AppResult.Failure(AppError.VinLookupFailed())
                    },
                    onFailure = { AppResult.Failure(AppError.VinLookupFailed(it)) },
                )
        }

    override suspend fun saveVehicle(vehicle: Vehicle): AppResult<Vehicle> =
        withContext(dispatchers.io) {
            vehicleDao.upsert(vehicle.toEntity())
            AppResult.Success(vehicle)
        }
}
