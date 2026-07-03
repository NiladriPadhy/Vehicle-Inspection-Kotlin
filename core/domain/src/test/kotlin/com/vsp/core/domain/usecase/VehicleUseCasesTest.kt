package com.vsp.core.domain.usecase

import com.vsp.core.domain.repository.VehicleRepository
import com.vsp.core.model.AppResult
import com.vsp.core.model.Vehicle
import com.vsp.core.model.VehicleCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeVehicleRepository : VehicleRepository {
    var saved: Vehicle? = null
    override fun observeVehicle(id: String): Flow<Vehicle?> = flowOf(null)
    override suspend fun decodeVin(vin: String): AppResult<Vehicle> =
        AppResult.Success(Vehicle(id = "v", vin = vin, decoded = true))
    override suspend fun scanVinFromImage(imagePath: String): AppResult<String> = AppResult.Success("VIN")
    override suspend fun saveVehicle(vehicle: Vehicle): AppResult<Vehicle> {
        saved = vehicle
        return AppResult.Success(vehicle)
    }
}

class VehicleUseCasesTest {

    @Test
    fun `decode rejects invalid vin without hitting repo`() = runTest {
        val useCase = DecodeVinUseCase(FakeVehicleRepository())
        val result = useCase("SHORT")
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `decode accepts valid vin`() = runTest {
        val useCase = DecodeVinUseCase(FakeVehicleRepository())
        val result = useCase("MAT625187NB123456")
        assertTrue(result is AppResult.Success)
    }

    @Test
    fun `old vehicle details require registration`() = runTest {
        val useCase = SaveOldVehicleDetailsUseCase(FakeVehicleRepository())
        val vehicle = Vehicle(id = "v", category = VehicleCategory.OLD, numberOfOwnerships = 1, numberOfKeys = 2)
        assertTrue(useCase(vehicle) is AppResult.Failure)
    }

    @Test
    fun `old vehicle details require ownership and keys`() = runTest {
        val useCase = SaveOldVehicleDetailsUseCase(FakeVehicleRepository())
        val vehicle = Vehicle(id = "v", category = VehicleCategory.OLD, registrationNumber = "KA01AB1234")
        assertTrue(useCase(vehicle) is AppResult.Failure)
    }

    @Test
    fun `complete old vehicle details persist`() = runTest {
        val repo = FakeVehicleRepository()
        val useCase = SaveOldVehicleDetailsUseCase(repo)
        val vehicle = Vehicle(
            id = "v",
            category = VehicleCategory.OLD,
            registrationNumber = "KA01AB1234",
            numberOfOwnerships = 1,
            numberOfKeys = 2,
        )
        assertTrue(useCase(vehicle) is AppResult.Success)
        assertTrue(repo.saved == vehicle)
    }

    @Test
    fun `save vehicle details rejects old without registration`() = runTest {
        val useCase = SaveVehicleDetailsUseCase(FakeVehicleRepository())
        val vehicle = Vehicle(id = "v", category = VehicleCategory.OLD)
        assertTrue(useCase(vehicle) is AppResult.Failure)
    }

    @Test
    fun `save vehicle details accepts new vehicle`() = runTest {
        val useCase = SaveVehicleDetailsUseCase(FakeVehicleRepository())
        val vehicle = Vehicle(id = "v", category = VehicleCategory.NEW)
        assertTrue(useCase(vehicle) is AppResult.Success)
    }
}
