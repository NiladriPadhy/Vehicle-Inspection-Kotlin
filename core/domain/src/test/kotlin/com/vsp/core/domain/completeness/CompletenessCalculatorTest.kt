package com.vsp.core.domain.completeness

import com.vsp.core.model.CaptureState
import com.vsp.core.model.DocumentType
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.Section
import com.vsp.core.model.VehicleCategory
import com.vsp.core.model.catalog.DocumentCatalog
import com.vsp.core.model.catalog.PositionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletenessCalculatorTest {

    private val calculator = CompletenessCalculator()

    private fun captured(section: Section, position: String, documentType: DocumentType? = null) =
        InspectionImage(
            id = "$section-$position",
            inspectionId = "insp",
            section = section,
            position = position,
            documentType = documentType,
            captureState = CaptureState.CAPTURED,
        )

    private fun allMandatoryCaptured(category: VehicleCategory): List<InspectionImage> = buildList {
        PositionCatalog.exterior.filter { it.mandatory }.forEach { add(captured(Section.EXTERIOR, it.id)) }
        PositionCatalog.interior.filter { it.mandatory }.forEach { add(captured(Section.INTERIOR, it.id)) }
        if (category == VehicleCategory.OLD) {
            DocumentCatalog.oldVehicleDocuments.forEach {
                add(captured(Section.DOCUMENT, it.type.name, it.type))
            }
        }
    }

    @Test
    fun `new vehicle complete when all mandatory positions captured`() {
        val result = calculator.calculate(VehicleCategory.NEW, allMandatoryCaptured(VehicleCategory.NEW))
        assertTrue(result.isComplete)
        assertTrue(result.missingPositions.isEmpty())
    }

    @Test
    fun `incomplete when a mandatory position is pending`() {
        val images = allMandatoryCaptured(VehicleCategory.NEW).drop(1)
        val result = calculator.calculate(VehicleCategory.NEW, images)
        assertFalse(result.isComplete)
        assertEquals(1, result.missingPositions.size)
    }

    @Test
    fun `skip with reason counts as addressed`() {
        val images = allMandatoryCaptured(VehicleCategory.NEW).drop(1) +
            InspectionImage(
                id = "skipped",
                inspectionId = "insp",
                section = PositionCatalog.exterior.first().section,
                position = PositionCatalog.exterior.first().id,
                captureState = CaptureState.SKIPPED,
                skipReason = "Vehicle against wall",
            )
        val result = calculator.calculate(VehicleCategory.NEW, images)
        assertTrue(result.isComplete)
    }

    @Test
    fun `skip without reason does not count as addressed`() {
        val images = allMandatoryCaptured(VehicleCategory.NEW).drop(1) +
            InspectionImage(
                id = "skipped",
                inspectionId = "insp",
                section = PositionCatalog.exterior.first().section,
                position = PositionCatalog.exterior.first().id,
                captureState = CaptureState.SKIPPED,
                skipReason = "  ",
            )
        val result = calculator.calculate(VehicleCategory.NEW, images)
        assertFalse(result.isComplete)
    }

    @Test
    fun `old vehicle requires documents in addition to positions`() {
        // Provide all position images but omit documents.
        val positionsOnly = buildList {
            PositionCatalog.exterior.filter { it.mandatory }.forEach { add(captured(Section.EXTERIOR, it.id)) }
            PositionCatalog.interior.filter { it.mandatory }.forEach { add(captured(Section.INTERIOR, it.id)) }
        }
        val result = calculator.calculate(VehicleCategory.OLD, positionsOnly)
        assertFalse(result.isComplete)
        assertEquals(DocumentType.entries.size, result.missingPositions.size)
    }

    @Test
    fun `old vehicle complete when positions and documents captured`() {
        val result = calculator.calculate(VehicleCategory.OLD, allMandatoryCaptured(VehicleCategory.OLD))
        assertTrue(result.isComplete)
    }
}
