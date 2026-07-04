package com.vsp.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vsp.core.data.local.VspDatabase
import com.vsp.core.data.local.dao.AiFindingDao
import com.vsp.core.data.local.dao.AnnotationDao
import com.vsp.core.data.local.dao.AppUserDao
import com.vsp.core.data.local.dao.AuditLogDao
import com.vsp.core.data.local.dao.ChecklistResponseDao
import com.vsp.core.data.local.dao.ConfigCacheDao
import com.vsp.core.data.local.dao.InspectionDao
import com.vsp.core.data.local.dao.InspectionImageDao
import com.vsp.core.data.local.dao.InspectorDao
import com.vsp.core.data.local.dao.ReportDao
import com.vsp.core.data.local.dao.SyncTaskDao
import com.vsp.core.data.local.dao.VehicleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Adds the checklist_responses table and vehicles.odometerKm column (see checklist plan). */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vehicles ADD COLUMN odometerKm INTEGER")
            db.execSQL("ALTER TABLE annotations ADD COLUMN component TEXT")
            db.execSQL("ALTER TABLE annotations ADD COLUMN vehicleSide TEXT")
            db.execSQL("ALTER TABLE annotations ADD COLUMN estimatedSize TEXT")
            db.execSQL("ALTER TABLE annotations ADD COLUMN repairRequired INTEGER")
            db.execSQL("ALTER TABLE annotations ADD COLUMN estimatedCost REAL")
            db.execSQL("ALTER TABLE annotations ADD COLUMN manualVerified INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS checklist_responses (
                    id TEXT NOT NULL PRIMARY KEY,
                    inspectionId TEXT NOT NULL,
                    itemId TEXT NOT NULL,
                    status TEXT,
                    rating INTEGER,
                    numericValue REAL,
                    textValue TEXT,
                    damageTypesCsv TEXT,
                    updatedAt INTEGER NOT NULL,
                    syncState TEXT NOT NULL,
                    FOREIGN KEY(inspectionId) REFERENCES inspections(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_responses_inspectionId ON checklist_responses(inspectionId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_checklist_responses_inspectionId_itemId ON checklist_responses(inspectionId, itemId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_responses_syncState ON checklist_responses(syncState)")
        }
    }

    /** Tags captured images with the checklist section that owns them (unique image set per section). */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE inspection_images ADD COLUMN checklistSectionId TEXT")
        }
    }

    /** Tags captured images with the specific checklist item (component) they document. */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE inspection_images ADD COLUMN checklistItemId TEXT")
        }
    }

    /**
     * Adds the questionnaire snapshot columns to inspections plus the config cache and local
     * credential-cache tables (feature 002: configurable checklist + custom auth + export/import).
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE inspections ADD COLUMN checklistVersion INTEGER")
            db.execSQL("ALTER TABLE inspections ADD COLUMN checklistHash TEXT")
            db.execSQL("ALTER TABLE inspections ADD COLUMN checklistSnapshotJson TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS config_cache (
                    type TEXT NOT NULL PRIMARY KEY,
                    version INTEGER NOT NULL,
                    hash TEXT NOT NULL,
                    json TEXT NOT NULL,
                    fetchedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_users (
                    uid TEXT NOT NULL PRIMARY KEY,
                    email TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    vendorId TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    algo TEXT NOT NULL,
                    iterations INTEGER NOT NULL,
                    salt TEXT NOT NULL,
                    hash TEXT NOT NULL,
                    cachedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_app_users_email ON app_users(email)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VspDatabase =
        Room.databaseBuilder(context, VspDatabase::class.java, VspDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun provideInspectorDao(db: VspDatabase): InspectorDao = db.inspectorDao()
    @Provides fun provideVehicleDao(db: VspDatabase): VehicleDao = db.vehicleDao()
    @Provides fun provideInspectionDao(db: VspDatabase): InspectionDao = db.inspectionDao()
    @Provides fun provideInspectionImageDao(db: VspDatabase): InspectionImageDao = db.inspectionImageDao()
    @Provides fun provideAiFindingDao(db: VspDatabase): AiFindingDao = db.aiFindingDao()
    @Provides fun provideAnnotationDao(db: VspDatabase): AnnotationDao = db.annotationDao()
    @Provides fun provideReportDao(db: VspDatabase): ReportDao = db.reportDao()
    @Provides fun provideAuditLogDao(db: VspDatabase): AuditLogDao = db.auditLogDao()
    @Provides fun provideSyncTaskDao(db: VspDatabase): SyncTaskDao = db.syncTaskDao()
    @Provides fun provideChecklistResponseDao(db: VspDatabase): ChecklistResponseDao = db.checklistResponseDao()
    @Provides fun provideConfigCacheDao(db: VspDatabase): ConfigCacheDao = db.configCacheDao()
    @Provides fun provideAppUserDao(db: VspDatabase): AppUserDao = db.appUserDao()
}
