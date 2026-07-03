package com.vsp.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vsp.core.data.local.dao.AiFindingDao
import com.vsp.core.data.local.dao.AnnotationDao
import com.vsp.core.data.local.dao.AuditLogDao
import com.vsp.core.data.local.dao.ChecklistResponseDao
import com.vsp.core.data.local.dao.InspectionDao
import com.vsp.core.data.local.dao.InspectionImageDao
import com.vsp.core.data.local.dao.InspectorDao
import com.vsp.core.data.local.dao.ReportDao
import com.vsp.core.data.local.dao.SyncTaskDao
import com.vsp.core.data.local.dao.VehicleDao
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

@Database(
    entities = [
        InspectorEntity::class,
        VehicleEntity::class,
        InspectionEntity::class,
        InspectionImageEntity::class,
        AiFindingEntity::class,
        AnnotationEntity::class,
        ReportEntity::class,
        AuditLogEntity::class,
        SyncTaskEntity::class,
        ChecklistResponseEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class VspDatabase : RoomDatabase() {
    abstract fun inspectorDao(): InspectorDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun inspectionImageDao(): InspectionImageDao
    abstract fun aiFindingDao(): AiFindingDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun reportDao(): ReportDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun syncTaskDao(): SyncTaskDao
    abstract fun checklistResponseDao(): ChecklistResponseDao

    companion object {
        const val NAME = "vsp.db"
    }
}
