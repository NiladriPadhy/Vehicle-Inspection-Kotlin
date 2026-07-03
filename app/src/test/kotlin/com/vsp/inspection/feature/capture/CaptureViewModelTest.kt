package com.vsp.inspection.feature.capture

import com.google.common.truth.Truth.assertThat
import com.vsp.core.domain.repository.ImageRepository
import com.vsp.core.domain.usecase.CaptureImageUseCase
import com.vsp.core.domain.usecase.ObserveImagesUseCase
import com.vsp.core.domain.usecase.SkipPositionUseCase
import com.vsp.core.model.AppResult
import com.vsp.core.model.CaptureState
import com.vsp.core.model.ImageQuality
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.Section
import com.vsp.core.model.catalog.PositionCatalog
import com.vsp.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.util.UUID

private class FakeImageRepository : ImageRepository {
    val images = MutableStateFlow<List<InspectionImage>>(emptyList())
    override fun observeImages(inspectionId: String): Flow<List<InspectionImage>> = images
    override fun observeImage(imageId: String): Flow<InspectionImage?> =
        images.map { list -> list.firstOrNull { it.id == imageId } }

    override suspend fun captureImage(inspectionId: String, section: Section, position: String, rawImagePath: String): AppResult<InspectionImage> {
        val image = InspectionImage(
            id = UUID.randomUUID().toString(),
            inspectionId = inspectionId,
            section = section,
            position = position,
            captureState = CaptureState.CAPTURED,
            localFilePath = rawImagePath,
            quality = ImageQuality.OK,
        )
        images.value = images.value + image
        return AppResult.Success(image)
    }

    override suspend fun captureSectionImage(
        inspectionId: String,
        section: Section,
        checklistSectionId: String,
        checklistItemId: String?,
        rawImagePath: String,
    ): AppResult<InspectionImage> {
        val image = InspectionImage(
            id = UUID.randomUUID().toString(),
            inspectionId = inspectionId,
            section = section,
            position = "${checklistItemId ?: checklistSectionId}_${UUID.randomUUID()}",
            checklistSectionId = checklistSectionId,
            checklistItemId = checklistItemId,
            captureState = CaptureState.CAPTURED,
            localFilePath = rawImagePath,
            quality = ImageQuality.OK,
        )
        images.value = images.value + image
        return AppResult.Success(image)
    }

    override suspend fun skipPosition(inspectionId: String, section: Section, position: String, reason: String): AppResult<Unit> {
        images.value = images.value + InspectionImage(
            id = UUID.randomUUID().toString(),
            inspectionId = inspectionId,
            section = section,
            position = position,
            captureState = CaptureState.SKIPPED,
            skipReason = reason,
        )
        return AppResult.Success(Unit)
    }

    override suspend fun deleteImage(imageId: String): AppResult<Unit> {
        images.value = images.value.filterNot { it.id == imageId }
        return AppResult.Success(Unit)
    }

    override suspend fun validateQuality(imagePath: String): AppResult<ImageQuality> = AppResult.Success(ImageQuality.OK)
}

@kotlinx.coroutines.ExperimentalCoroutinesApi
class CaptureViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(repo: FakeImageRepository) = CaptureViewModel(
        observeImages = ObserveImagesUseCase(repo),
        captureImage = CaptureImageUseCase(repo),
        skipPosition = SkipPositionUseCase(repo),
    )

    @Test
    fun `wizard starts at first position`() = runTest {
        val vm = viewModel(FakeImageRepository())
        vm.initialize("insp", Section.EXTERIOR)
        advanceUntilIdle()
        assertThat(vm.state.value.currentIndex).isEqualTo(0)
        assertThat(vm.state.value.current?.id).isEqualTo(PositionCatalog.exterior.first().id)
    }

    @Test
    fun `capturing advances strictly to the next position`() = runTest {
        val repo = FakeImageRepository()
        val vm = viewModel(repo)
        vm.initialize("insp", Section.EXTERIOR)
        advanceUntilIdle()

        vm.capture("/tmp/a.jpg")
        advanceUntilIdle()

        assertThat(vm.state.value.currentIndex).isEqualTo(1)
        assertThat(vm.state.value.current?.id).isEqualTo(PositionCatalog.exterior[1].id)
        assertThat(vm.state.value.addressedCount).isEqualTo(1)
    }

    @Test
    fun `skipping with reason advances the wizard`() = runTest {
        val repo = FakeImageRepository()
        val vm = viewModel(repo)
        vm.initialize("insp", Section.EXTERIOR)
        advanceUntilIdle()

        vm.skip("Not accessible")
        advanceUntilIdle()

        assertThat(vm.state.value.currentIndex).isEqualTo(1)
    }
}
