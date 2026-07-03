package com.vsp.core.data.di

import com.vsp.core.data.repository.AiAnalysisRepositoryImpl
import com.vsp.core.data.repository.AnnotationRepositoryImpl
import com.vsp.core.data.repository.AuthRepositoryImpl
import com.vsp.core.data.repository.ChecklistRepositoryImpl
import com.vsp.core.data.repository.ImageRepositoryImpl
import com.vsp.core.data.repository.InspectionRepositoryImpl
import com.vsp.core.data.repository.ReportRepositoryImpl
import com.vsp.core.data.repository.SyncRepositoryImpl
import com.vsp.core.data.repository.VehicleRepositoryImpl
import com.vsp.core.domain.repository.AiAnalysisRepository
import com.vsp.core.domain.repository.AnnotationRepository
import com.vsp.core.domain.repository.AuthRepository
import com.vsp.core.domain.repository.ChecklistRepository
import com.vsp.core.domain.repository.ImageRepository
import com.vsp.core.domain.repository.InspectionRepository
import com.vsp.core.domain.repository.ReportRepository
import com.vsp.core.domain.repository.SyncRepository
import com.vsp.core.domain.repository.VehicleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindVehicleRepository(impl: VehicleRepositoryImpl): VehicleRepository

    @Binds @Singleton
    abstract fun bindInspectionRepository(impl: InspectionRepositoryImpl): InspectionRepository

    @Binds @Singleton
    abstract fun bindImageRepository(impl: ImageRepositoryImpl): ImageRepository

    @Binds @Singleton
    abstract fun bindAnnotationRepository(impl: AnnotationRepositoryImpl): AnnotationRepository

    @Binds @Singleton
    abstract fun bindAiAnalysisRepository(impl: AiAnalysisRepositoryImpl): AiAnalysisRepository

    @Binds @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository

    @Binds @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds @Singleton
    abstract fun bindChecklistRepository(impl: ChecklistRepositoryImpl): ChecklistRepository
}
