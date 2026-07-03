package com.vsp.core.domain.repository

import com.vsp.core.model.AIFinding
import com.vsp.core.model.Annotation
import com.vsp.core.model.AppResult
import com.vsp.core.model.ChecklistResponse
import com.vsp.core.model.Completeness
import com.vsp.core.model.FinalVerification
import com.vsp.core.model.ImageQuality
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
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val session: Flow<Session?>
    suspend fun signIn(email: String, password: String): AppResult<Session>
    suspend fun signOut(): AppResult<Unit>
    fun hasValidOfflineSession(): Boolean
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
