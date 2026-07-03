# Repository & Port Contracts (domain)

Interfaces live in `:core:domain`; implementations in `:core:data`. Reads return `Flow`;
mutations are `suspend` and return `Result<T, AppError>`. No Android/vendor types appear
in these signatures.

```kotlin
// Result & error (in :core:common)
sealed interface AppError { /* Network, Auth, Permission, ImageQuality, AiUnavailable,
                               AiInvalidResponse, VinLookupFailed, Storage, Unknown */ }
typealias AppResult<T> = Result<T> // domain Result<T, AppError> wrapper

interface AuthRepository {
    val session: Flow<Session?>
    suspend fun signIn(email: String, password: String): AppResult<Session>
    suspend fun signOut(): AppResult<Unit>
    fun hasValidOfflineSession(): Boolean
}

interface VehicleRepository {
    suspend fun decodeVin(vin: String): AppResult<Vehicle>            // may fail -> manual fallback
    suspend fun scanVinFromImage(imagePath: String): AppResult<String> // OCR -> VIN string
    suspend fun saveVehicle(vehicle: Vehicle): AppResult<Vehicle>
    fun observeVehicle(id: String): Flow<Vehicle?>
}

interface InspectionRepository {
    fun observeInspections(inspectorId: String): Flow<List<Inspection>>
    fun observeInspection(id: String): Flow<Inspection?>
    suspend fun startInspection(inspectorId: String, context: InspectionContext,
                                category: VehicleCategory): AppResult<Inspection>
    suspend fun updateStep(id: String, step: String): AppResult<Unit>
    suspend fun getCompleteness(id: String): AppResult<Completeness>
    suspend fun finalize(id: String): AppResult<Unit>                 // gated by >=1 captured photo
    suspend fun deleteInspection(id: String): AppResult<Unit>         // cascades DB rows + files
}

interface ImageRepository {
    fun observeImages(inspectionId: String): Flow<List<InspectionImage>>
    fun observeImage(imageId: String): Flow<InspectionImage?>
    suspend fun captureImage(inspectionId: String, section: Section, position: String,
                             rawImagePath: String): AppResult<InspectionImage> // validate+compress+store
    // Checklist-first capture: photo is tagged to a catalog section + optional item.
    suspend fun captureSectionImage(inspectionId: String, section: Section, checklistSectionId: String,
                                    checklistItemId: String?, rawImagePath: String): AppResult<InspectionImage>
    suspend fun skipPosition(inspectionId: String, section: Section, position: String,
                             reason: String): AppResult<Unit>
    suspend fun deleteImage(imageId: String): AppResult<Unit>          // removes DB row + file
    suspend fun validateQuality(imagePath: String): AppResult<ImageQuality>
}

// Catalog-driven checklist responses (checklist-first workflow).
interface ChecklistRepository {
    fun observeResponses(inspectionId: String): Flow<List<ChecklistResponse>>
    suspend fun save(response: ChecklistResponse): AppResult<Unit>
}

interface AnnotationRepository {
    fun observeAnnotations(imageId: String): Flow<List<Annotation>>
    suspend fun add(annotation: Annotation): AppResult<Annotation>
    suspend fun update(annotation: Annotation): AppResult<Unit>
    suspend fun delete(annotationId: String): AppResult<Unit>
}

interface AiAnalysisRepository {
    fun observeFindings(imageId: String): Flow<List<AIFinding>>
    suspend fun analyzeImage(image: InspectionImage): AppResult<List<AIFinding>>      // validated
    suspend fun reverifyAnnotation(image: InspectionImage,
                                   annotation: Annotation): AppResult<ReverifyResult> // validated
    suspend fun runFinalVerification(inspectionId: String): AppResult<FinalVerification> // validated
}

interface ReportRepository {
    suspend fun generate(inspectionId: String): AppResult<Report>     // rebuilds JSON from current graph
    fun observeReport(inspectionId: String): Flow<Report?>
    suspend fun share(inspectionId: String): AppResult<Unit>
    suspend fun exportPdf(inspectionId: String): AppResult<String>    // branded multi-section PDF path
}

interface SyncRepository {
    fun observeSyncStatus(inspectionId: String): Flow<SyncSummary>
    suspend fun enqueue(entityType: String, entityId: String, op: String): AppResult<Unit>
    suspend fun retryFailed(inspectionId: String): AppResult<Unit>
}

// Ports abstracting vendor SDKs (implemented in :core:data / :core:camera)
interface AiVisionPort {                       // wraps Gemini Vision
    suspend fun detect(imageBytes: ByteArray, prompt: AiPrompt): RawAiResponse
}
interface VinDecodeSource { suspend fun decode(vin: String): VehicleDecodeDto? }
interface DispatcherProvider { val io: CoroutineDispatcher; val default: CoroutineDispatcher; val main: CoroutineDispatcher }
```
