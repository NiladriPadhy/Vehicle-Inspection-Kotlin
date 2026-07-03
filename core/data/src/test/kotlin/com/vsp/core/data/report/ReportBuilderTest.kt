package com.vsp.core.data.report

import com.google.common.truth.Truth.assertThat
import com.vsp.core.model.BoundingBox
import com.vsp.core.model.CaptureState
import com.vsp.core.model.DamageType
import com.vsp.core.model.FindingSource
import com.vsp.core.model.AIFinding
import com.vsp.core.model.Inspection
import com.vsp.core.model.InspectionContext
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.InspectionStatus
import com.vsp.core.model.Inspector
import com.vsp.core.model.Section
import com.vsp.core.model.Severity
import com.vsp.core.model.Vehicle
import com.vsp.core.model.VehicleCategory
import org.junit.Test

class ReportBuilderTest {

    private val builder = ReportBuilder()

    private fun inspection() = Inspection(
        id = "insp1",
        inspectorId = "u1",
        vehicleId = "v1",
        context = InspectionContext.RESALE,
        vehicleCategory = VehicleCategory.OLD,
        status = InspectionStatus.COMPLETED,
        currentStep = "REPORT",
        createdAt = 1000L,
        updatedAt = 2000L,
        completedAt = 2000L,
        exteriorScore = 80,
        interiorScore = 70,
        safetyScore = 90,
        cosmeticScore = 60,
        confidenceScore = 85,
        overallCondition = "GOOD",
        finalRecommendation = "PASS",
    )

    @Test
    fun `builds report with vehicle category and severity counts`() {
        val vehicle = Vehicle(id = "v1", category = VehicleCategory.OLD, make = "Tata", model = "Nexon", numberOfKeys = 2, numberOfOwnerships = 1)
        val image = InspectionImage(id = "im1", inspectionId = "insp1", section = Section.EXTERIOR, position = "FRONT", captureState = CaptureState.CAPTURED)
        val finding = AIFinding("f1", "im1", DamageType.DENT, 0.9f, Severity.HIGH, BoundingBox(0f, 0f, 0.1f, 0.1f), "fix", false, FindingSource.INITIAL, 1L)

        val dto = builder.build(
            reportId = "r1",
            inspection = inspection(),
            vehicle = vehicle,
            inspector = Inspector("u1", "Alice", "a@b.com"),
            device = ReportDeviceDto("Pixel", "Android 14", "0.1.0"),
            images = listOf(ReportBuilder.ImageBundle(image, emptyList(), listOf(finding))),
        )

        assertThat(dto.vehicle.category).isEqualTo("OLD")
        assertThat(dto.vehicle.numberOfKeys).isEqualTo(2)
        assertThat(dto.damageSummary.totalDamageCount).isEqualTo(1)
        assertThat(dto.damageSummary.bySeverity.high).isEqualTo(1)
        assertThat(dto.scores.confidence).isEqualTo(85)

        val json = builder.toJson(dto)
        assertThat(json).contains("\"reportId\": \"r1\"")
        assertThat(json).contains("\"category\": \"OLD\"")
    }

    @Test
    fun `checklist-tagged images are nested under their checklist item`() {
        val vehicle = Vehicle(id = "v1", category = VehicleCategory.OLD, make = "Tata", model = "Nexon")
        val taggedImage = InspectionImage(
            id = "im2",
            inspectionId = "insp1",
            section = Section.EXTERIOR,
            position = "ext_front_bumper_im2",
            checklistSectionId = "exterior",
            checklistItemId = "ext_front_bumper",
            captureState = CaptureState.CAPTURED,
        )

        val dto = builder.build(
            reportId = "r1",
            inspection = inspection(),
            vehicle = vehicle,
            inspector = Inspector("u1", "Alice", "a@b.com"),
            device = ReportDeviceDto("Pixel", "Android 14", "0.1.0"),
            images = listOf(ReportBuilder.ImageBundle(taggedImage, emptyList(), emptyList())),
        )

        // Tagged image is nested under its checklist item, not in the flat top-level list.
        assertThat(dto.images).isEmpty()
        val item = dto.checklist.flatMap { it.items }.firstOrNull { it.itemId == "ext_front_bumper" }
        assertThat(item).isNotNull()
        assertThat(item!!.images.map { it.imageId }).containsExactly("im2")
        assertThat(item.images.first().checklistItem).isEqualTo("Front Bumper")
    }
}
