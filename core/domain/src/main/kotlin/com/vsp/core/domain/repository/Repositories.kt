package com.vsp.core.domain.repository

import com.vsp.core.model.AIFinding
import com.vsp.core.model.Annotation
import com.vsp.core.model.AppResult
import com.vsp.core.model.ChecklistResponse
import com.vsp.core.model.Completeness
import com.vsp.core.model.ExportResult
import com.vsp.core.model.FinalVerification
import com.vsp.core.model.ImageQuality
import com.vsp.core.model.ImportPreview
import com.vsp.core.model.ImportResult
import com.vsp.core.model.Inspection
import com.vsp.core.model.InspectionContext
import com.vsp.core.model.InspectionImage
import com.vsp.core.model.Report
import com.vsp.core.model.ReverifyResult
import com.vsp.core.model.Section
import com.vsp.core.model.Session
import com.vsp.core.model.SyncSummary
import com.vsp.core.model.Vehicle
import com.vsp.core.model.VehicleCategory
import com.vsp.core.model.config.BrandingConfig
import com.vsp.core.model.config.QuestionnaireConfig
import com.vsp.core.model.config.VehicleCatalog
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val session: Flow<Session?>
    suspend fun signIn(email: String, password: String): AppResult<Session>
    suspend fun signUp(displayName: String, email: String, password: String): AppResult<Session>
    suspend fun signOut(): AppResult<Unit>
    fun hasValidOfflineSession(): Boolean
}

/**
 * Vendor configuration (questionnaire + vehicle catalog) fetched from Firebase RTDB, cached locally,
 * and version-pinned. Adoption of a newer version is gated (feature 002 §10): only when there is no
 * local inspection data or at a login boundary.
 */
interface ConfigRepository {
    /** The questionnaire currently used for NEW inspections (cache → baseline fallback). */
    suspend fun activeQuestionnaire(): QuestionnaireConfig

    /** Fetches remote config (seeding baseline for a fresh vendor DB) and adopts it if gating allows. */
    suspend fun syncOnLogin(): AppResult<QuestionnaireConfig>

    /** The active vehicle make/model/variant catalog, if any. */
    suspend fun vehicleCatalog(): VehicleCatalog?

    /** The active vendor report branding/theme (cache → default fallback). */
    suspend fun activeBranding(): BrandingConfig
}

interface ExportRepository {
    /** Bundles every inspection for the inspector (images + CSV + manifest) into a shareable zip. */
    suspend fun exportAll(inspectorId: String): AppResult<ExportResult>
}

interface ImportRepository {
    /** Validates a bundle (structure + questionnaire/CSV compatibility) without applying it. */
    suspend fun validate(zipPath: String): AppResult<ImportPreview>

    /** Validates then applies a bundle to the local store (device migration). */
    suspend fun import(zipPath: String, inspectorId: String): AppResult<ImportResult>
}

interface VehicleRepository {
    fun observeVehicle(id: String): Flow<Vehicle?>
    suspend fun decodeVin(vin: String): AppResult<Vehicle>
    suspend fun scanVinFromImage(imagePath: String): AppResult<String>
    suspend fun saveVehicle(vehicle: Vehicle): AppResult<Vehicle>
}

interface InspectionRepository {
    fun observeInspections(inspectorId: String): Flow<List<Inspection>>
    fun observeInspection(id: String): Flow<Inspection?>
    suspend fun startInspection(
        inspectorId: String,
        context: InspectionContext,
        category: VehicleCategory,
    ): AppResult<Inspection>
    suspend fun updateStep(id: String, step: String): AppResult<Unit>
    suspend fun getCompleteness(id: String): AppResult<Completeness>
    suspend fun finalize(id: String): AppResult<Unit>
    suspend fun deleteInspection(id: String): AppResult<Unit>

    /** The questionnaire pinned to this inspection (its snapshot), falling back to the active config. */
    suspend fun questionnaireFor(id: String): QuestionnaireConfig
}

interface ImageRepository {
    fun observeImages(inspectionId: String): Flow<List<InspectionImage>>
    fun observeImage(imageId: String): Flow<InspectionImage?>
    suspend fun captureImage(
        inspectionId: String,
        section: Section,
        position: String,
        rawImagePath: String,
    ): AppResult<InspectionImage>
    suspend fun captureSectionImage(
        inspectionId: String,
        section: Section,
        checklistSectionId: String,
        checklistItemId: String?,
        rawImagePath: String,
    ): AppResult<InspectionImage>
    suspend fun skipPosition(
        inspectionId: String,
        section: Section,
        position: String,
        reason: String,
    ): AppResult<Unit>
    suspend fun deleteImage(imageId: String): AppResult<Unit>
    suspend fun validateQuality(imagePath: String): AppResult<ImageQuality>
}

interface AnnotationRepository {
    fun observeAnnotations(imageId: String): Flow<List<Annotation>>
    suspend fun add(annotation: Annotation): AppResult<Annotation>
    suspend fun update(annotation: Annotation): AppResult<Unit>
    suspend fun delete(annotationId: String): AppResult<Unit>
}

interface AiAnalysisRepository {
    fun observeFindings(imageId: String): Flow<List<AIFinding>>
    suspend fun analyzeImage(image: InspectionImage): AppResult<List<AIFinding>>
    suspend fun reverifyAnnotation(image: InspectionImage, annotation: Annotation): AppResult<ReverifyResult>
    suspend fun runFinalVerification(inspectionId: String): AppResult<FinalVerification>
}

interface ReportRepository {
    fun observeReport(inspectionId: String): Flow<Report?>
    suspend fun generate(inspectionId: String): AppResult<Report>
    suspend fun share(inspectionId: String): AppResult<Unit>

    /** Renders a human-readable PDF report and returns the absolute file path. */
    suspend fun exportPdf(inspectionId: String): AppResult<String>
}

interface ChecklistRepository {
    fun observeResponses(inspectionId: String): Flow<List<ChecklistResponse>>
    suspend fun save(response: ChecklistResponse): AppResult<Unit>
}

interface SyncRepository {
    fun observeSyncStatus(inspectionId: String): Flow<SyncSummary>
    suspend fun enqueue(entityType: String, entityId: String, op: String): AppResult<Unit>
    suspend fun retryFailed(inspectionId: String): AppResult<Unit>
}
