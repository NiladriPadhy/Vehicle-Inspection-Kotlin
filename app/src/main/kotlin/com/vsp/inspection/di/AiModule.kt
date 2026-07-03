package com.vsp.inspection.di

import com.vsp.core.data.ai.AiConfig
import com.vsp.inspection.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Supplies the Gemini Vision configuration from BuildConfig (backed by local.properties /
 * GEMINI_API_KEY env var). When the key is blank the vision port reports the model as
 * unavailable and the app degrades gracefully (offline-first).
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAiConfig(): AiConfig = AiConfig(
        apiKey = BuildConfig.GEMINI_API_KEY,
        model = BuildConfig.GEMINI_MODEL,
    )
}
