package com.vsp.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.vsp.core.data.local.entity.AiFindingEntity
import com.vsp.core.data.local.entity.AnnotationEntity
import com.vsp.core.data.local.entity.AuditLogEntity
import com.vsp.core.data.local.entity.ChecklistResponseEntity
import com.vsp.core.data.local.entity.InspectionEntity
import com.vsp.core.data.local.entity.InspectionImageEntity
import com.vsp.core.data.local.entity.InspectorEntity
import com.vsp.core.data.local.entity.ReportEntity
import com.vsp.core.data.local.entity.SyncTaskEntity
import com.vsp.core.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectorDao {
    @Upsert suspend fun upsert(inspector: InspectorEntity)
    @Query("SELECT * FROM inspectors WHERE id = :id") suspend fun getById(id: String): InspectorEntity?
}

@Dao
interface VehicleDao {
    @Upsert suspend fun upsert(vehicle: VehicleEntity)
    @Query("SELECT * FROM vehicles WHERE id = :id") fun observe(id: String): Flow<VehicleEntity?>
    @Query("SELECT * FROM vehicles WHERE id = :id") suspend fun getById(id: String): VehicleEntity?
}

@Dao
interface InspectionDao {
    @Upsert suspend fun upsert(inspection: InspectionEntity)
    @Query("SELECT * FROM inspections WHERE inspectorId = :inspectorId ORDER BY updatedAt DESC")
    fun observeForInspector(inspectorId: String): Flow<List<InspectionEntity>>
    @Query("SELECT * FROM inspections WHERE id = :id") fun observe(id: String): Flow<InspectionEntity?>
    @Query("SELECT * FROM inspections WHERE id = :id") suspend fun getById(id: String): InspectionEntity?
    @Query("UPDATE inspections SET currentStep = :step, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStep(id: String, step: String, updatedAt: Long)
    @Query("DELETE FROM inspections WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao
interface InspectionImageDao {
    @Upsert suspend fun upsert(image: InspectionImageEntity)
    @Query("SELECT * FROM inspection_images WHERE inspectionId = :inspectionId ORDER BY section, position")
    fun observeForInspection(inspectionId: String): Flow<List<InspectionImageEntity>>
    @Query("SELECT * FROM inspection_images WHERE inspectionId = :inspectionId")
    suspend fun getForInspection(inspectionId: String): List<InspectionImageEntity>
    @Query("SELECT * FROM inspection_images WHERE id = :id") suspend fun getById(id: String): InspectionImageEntity?
    @Query("SELECT * FROM inspection_images WHERE id = :id") fun observeById(id: String): Flow<InspectionImageEntity?>
    @Query("DELETE FROM inspection_images WHERE id = :id") suspend fun deleteById(id: String)
    @Query("SELECT * FROM inspection_images WHERE inspectionId = :inspectionId AND section = :section AND position = :position LIMIT 1")
    suspend fun findSlot(inspectionId: String, section: String, position: String): InspectionImageEntity?
    @Query("SELECT * FROM inspection_images WHERE captureState = 'CAPTURED' AND syncState IN ('PENDING','FAILED')")
    suspend fun getPendingUploads(): List<InspectionImageEntity>
}

@Dao
interface AiFindingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(findings: List<AiFindingEntity>)
    @Query("SELECT * FROM ai_findings WHERE imageId = :imageId") fun observeForImage(imageId: String): Flow<List<AiFindingEntity>>
    @Query("SELECT * FROM ai_findings WHERE imageId = :imageId") suspend fun getForImage(imageId: String): List<AiFindingEntity>
    @Query("DELETE FROM ai_findings WHERE imageId = :imageId AND source = :source")
    suspend fun deleteForImageBySource(imageId: String, source: String)
}

@Dao
interface AnnotationDao {
    @Insert suspend fun insert(annotation: AnnotationEntity)
    @Update suspend fun update(annotation: AnnotationEntity)
    @Query("DELETE FROM annotations WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM annotations WHERE id = :id") suspend fun getById(id: String): AnnotationEntity?
    @Query("SELECT * FROM annotations WHERE imageId = :imageId ORDER BY createdAt")
    fun observeForImage(imageId: String): Flow<List<AnnotationEntity>>
    @Query("SELECT * FROM annotations WHERE imageId = :imageId ORDER BY createdAt")
    suspend fun getForImage(imageId: String): List<AnnotationEntity>
}

@Dao
interface ReportDao {
    @Upsert suspend fun upsert(report: ReportEntity)
    @Query("SELECT * FROM reports WHERE inspectionId = :inspectionId") fun observe(inspectionId: String): Flow<ReportEntity?>
    @Query("SELECT * FROM reports WHERE inspectionId = :inspectionId") suspend fun getForInspection(inspectionId: String): ReportEntity?
    @Query("SELECT * FROM reports WHERE syncState IN ('PENDING','FAILED')") suspend fun getPendingUploads(): List<ReportEntity>
}

@Dao
interface AuditLogDao {
    @Insert suspend fun insert(entry: AuditLogEntity)
    @Query("SELECT * FROM audit_log WHERE inspectionId = :inspectionId ORDER BY timestamp")
    fun observeForInspection(inspectionId: String): Flow<List<AuditLogEntity>>
}

@Dao
interface ChecklistResponseDao {
    @Upsert suspend fun upsert(response: ChecklistResponseEntity)
    @Query("SELECT * FROM checklist_responses WHERE inspectionId = :inspectionId")
    fun observeForInspection(inspectionId: String): Flow<List<ChecklistResponseEntity>>
    @Query("SELECT * FROM checklist_responses WHERE inspectionId = :inspectionId")
    suspend fun getForInspection(inspectionId: String): List<ChecklistResponseEntity>
    @Query("SELECT * FROM checklist_responses WHERE inspectionId = :inspectionId AND itemId = :itemId LIMIT 1")
    suspend fun getItem(inspectionId: String, itemId: String): ChecklistResponseEntity?
}

@Dao
interface SyncTaskDao {
    @Upsert suspend fun upsert(task: SyncTaskEntity)
    @Query("SELECT * FROM sync_tasks WHERE status = :status") suspend fun getByStatus(status: String): List<SyncTaskEntity>
    @Query("SELECT * FROM sync_tasks") fun observeAll(): Flow<List<SyncTaskEntity>>
    @Query("DELETE FROM sync_tasks WHERE id = :id") suspend fun delete(id: String)
}
