package com.vsp.inspection.di

import com.vsp.core.data.portability.BuildConfigVendor
import com.vsp.core.data.remote.rtdb.FirebaseConfig
import com.vsp.inspection.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Supplies the per-vendor Firebase Realtime Database configuration from BuildConfig (backed by
 * local.properties). When the URL is blank the app runs in offline baseline mode: RTDB is disabled
 * and the bundled baseline questionnaire is used.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseConfig(): FirebaseConfig {
        BuildConfigVendor.vendorId = BuildConfig.VENDOR_ID
        return FirebaseConfig(
            databaseUrl = BuildConfig.FIREBASE_DB_URL,
            projectId = BuildConfig.FIREBASE_PROJECT_ID,
            applicationId = BuildConfig.FIREBASE_APP_ID,
            apiKey = BuildConfig.FIREBASE_API_KEY,
            vendorId = BuildConfig.VENDOR_ID,
        )
    }
}
