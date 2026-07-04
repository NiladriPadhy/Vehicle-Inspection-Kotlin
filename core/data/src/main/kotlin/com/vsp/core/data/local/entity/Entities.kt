package com.vsp.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "inspectors")
data class InspectorEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val email: String,
)

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val vin: String?,
    val category: String,
    val year: Int?,
    val manufacturer: String?,
    val make: String?,
    val model: String?,
    val variant: String?,
    val trim: String?,
    val bodyStyle: String?,
    val fuelType: String?,
    val transmission: String?,
    val color: String?,
    val registrationNumber: String?,
    val engineNumber: String?,
    val chassisNumber: String?,
    val numberOfOwnerships: Int?,
    val numberOfKeys: Int?,
    val odometerKm: Int? = null,
    val vinInputMethod: String,
    val decoded: Boolean,
)

@Entity(
    tableName = "inspections",
    foreignKeys = [
        ForeignKey(
            entity = InspectorEntity::class,
            parentColumns = ["id"],
            childColumns = ["inspectorId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("inspectorId"), Index("vehicleId"), Index("status"), Index("syncState")],
)
data class InspectionEntity(
    @PrimaryKey val id: String,
    val inspectorId: String,
    val vehicleId: String,
    val context: String,
    val vehicleCategory: String,
    val status: String,
    val currentStep: String,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val gpsLat: Double?,
    val gpsLng: Double?,
    val deviceInfo: String,
    val exteriorScore: Int?,
    val interiorScore: Int?,
    val safetyScore: Int?,
    val cosmeticScore: Int?,
    val confidenceScore: Int?,
    val overallCondition: String?,
    val finalRecommendation: String?,
    val summary: String?,
    val syncState: String,
    // Snapshot of the questionnaire this inspection was created with (feature 002 §9). Pinning the
    // definition here means later Firebase config edits never mutate an in-flight inspection.
    val checklistVersion: Int? = null,
    val checklistHash: String? = null,
    val checklistSnapshotJson: String? = null,
)

@Entity(
    tableName = "inspection_images",
    foreignKeys = [
        ForeignKey(
            entity = InspectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["inspectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("inspectionId"),
        Index(value = ["inspectionId", "section", "position"], unique = true),
        Index("syncState"),
        Index("aiState"),
    ],
)
data class InspectionImageEntity(
    @PrimaryKey val id: String,
    val inspectionId: String,
    val section: String,
    val position: String,
    val documentType: String?,
    val checklistSectionId: String? = null,
    val checklistItemId: String? = null,
    val captureState: String,
    val skipReason: String?,
    val localFilePath: String,
    val thumbnailPath: String?,
    val remoteUrl: String?,
    val width: Int?,
    val height: Int?,
    val sizeBytes: Long?,
    val capturedAt: Long?,
    val orientation: Int?,
    val quality: String,
    val aiState: String,
    val syncState: String,
)

@Entity(
    tableName = "ai_findings",
    foreignKeys = [
        ForeignKey(
            entity = InspectionImageEntity::class,
            parentColumns = ["id"],
            childColumns = ["imageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("imageId")],
)
data class AiFindingEntity(
    @PrimaryKey val id: String,
    val imageId: String,
    val damageType: String,
    val confidence: Float,
    val severity: String,
    val bboxX: Float,
    val bboxY: Float,
    val bboxW: Float,
    val bboxH: Float,
    val repairRecommendation: String,
    val reviewRequired: Boolean,
    val source: String,
    val createdAt: Long,
)

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = InspectionImageEntity::class,
            parentColumns = ["id"],
            childColumns = ["imageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("imageId")],
)
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val imageId: String,
    val shape: String,
    val geometryJson: String,
    val damageType: String,
    val severity: String,
    val comment: String?,
    val component: String? = null,
    val vehicleSide: String? = null,
    val estimatedSize: String? = null,
    val repairRequired: Boolean? = null,
    val estimatedCost: Double? = null,
    val manualVerified: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "reports",
    foreignKeys = [
        ForeignKey(
            entity = InspectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["inspectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["inspectionId"], unique = true)],
)
data class ReportEntity(
    @PrimaryKey val id: String,
    val inspectionId: String,
    val json: String,
    val localJsonPath: String?,
    val remoteJsonUrl: String?,
    val generatedAt: Long,
    val status: String,
    val syncState: String,
)

@Entity(
    tableName = "audit_log",
    foreignKeys = [
        ForeignKey(
            entity = InspectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["inspectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("inspectionId")],
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val inspectionId: String,
    val eventType: String,
    val actorId: String,
    val timestamp: Long,
    val detailJson: String?,
)

@Entity(
    tableName = "checklist_responses",
    foreignKeys = [
        ForeignKey(
            entity = InspectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["inspectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("inspectionId"),
        Index(value = ["inspectionId", "itemId"], unique = true),
        Index("syncState"),
    ],
)
data class ChecklistResponseEntity(
    @PrimaryKey val id: String,
    val inspectionId: String,
    val itemId: String,
    val status: String?,
    val rating: Int?,
    val numericValue: Double?,
    val textValue: String?,
    val damageTypesCsv: String?,
    val updatedAt: Long,
    val syncState: String,
)

/** Cached active vendor configuration (questionnaire / vehicle catalog). One row per [type]. */
@Entity(tableName = "config_cache")
data class ConfigCacheEntity(
    @PrimaryKey val type: String,
    val version: Int,
    val hash: String,
    val json: String,
    val fetchedAt: Long,
)

/** Local credential cache for offline re-login (custom RTDB auth). Passwords stored hashed only. */
@Entity(tableName = "app_users", indices = [Index(value = ["email"], unique = true)])
data class AppUserEntity(
    @PrimaryKey val uid: String,
    val email: String,
    val displayName: String,
    val vendorId: String,
    val createdAt: Long,
    val algo: String,
    val iterations: Int,
    val salt: String,
    val hash: String,
    val cachedAt: Long,
)

@Entity(tableName = "sync_tasks", indices = [Index("status")])
data class SyncTaskEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val status: String,
    val attemptCount: Int,
    val lastAttemptAt: Long?,
    val lastError: String?,
)
