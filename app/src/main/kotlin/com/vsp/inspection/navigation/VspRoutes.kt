package com.vsp.inspection.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe route definitions for the app navigation graph. Mirrors the wizard defined in
 * the plan: Login -> Dashboard -> (Start [VIN + New/Old]) -> Identify -> Details ->
 * [Old-vehicle Docs, if Old] -> Exterior -> Interior -> Review -> Final Verification ->
 * Report. Feature modules will contribute their own NavGraphBuilder extensions as they are
 * implemented (US1-US9).
 */
sealed interface VspRoute {
    @Serializable data object Login : VspRoute
    @Serializable data object SignUp : VspRoute
    @Serializable data object Dashboard : VspRoute
    @Serializable data object DataManagement : VspRoute

    @Serializable data object StartInspection : VspRoute
    @Serializable data class IdentifyVehicle(val inspectionId: String) : VspRoute
    @Serializable data class OldVehicleDocs(val inspectionId: String) : VspRoute
    @Serializable data class ExteriorCapture(val inspectionId: String) : VspRoute
    @Serializable data class InteriorCapture(val inspectionId: String) : VspRoute
    @Serializable data class ImageDetail(
        val imageId: String,
        val checklistSectionId: String? = null,
        val checklistItemId: String? = null,
    ) : VspRoute
    @Serializable data class Review(val inspectionId: String) : VspRoute
    @Serializable data class ChecklistHub(val inspectionId: String) : VspRoute
    @Serializable data class ChecklistSection(val inspectionId: String, val sectionId: String) : VspRoute
    @Serializable data class SectionCapture(
        val inspectionId: String,
        val sectionId: String,
        val section: String,
        val itemId: String? = null,
    ) : VspRoute
    @Serializable data class FinalVerification(val inspectionId: String) : VspRoute
    @Serializable data class Report(val inspectionId: String) : VspRoute
}
