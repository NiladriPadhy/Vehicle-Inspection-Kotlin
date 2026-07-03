package com.vsp.core.data.di

import com.vsp.core.data.ai.GeminiAiVisionPort
import com.vsp.core.data.vin.VinDecoder
import com.vsp.core.domain.port.AiVisionPort
import com.vsp.core.domain.port.VinDecodeSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds vendor ports. The `AiConfig` (Gemini key/model) is provided by the app module from
 * BuildConfig so the key can be sourced from local.properties without hard-coding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PortModule {

    @Binds @Singleton
    abstract fun bindAiVisionPort(impl: GeminiAiVisionPort): AiVisionPort

    @Binds @Singleton
    abstract fun bindVinDecodeSource(impl: VinDecoder): VinDecodeSource
}
