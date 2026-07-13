package com.visionboard.di

import com.visionboard.camera.analyzer.KeyboardFrameAnalyzer
import com.visionboard.camera.config.CameraConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CameraModule {

    @Provides
    @Singleton
    fun provideCameraConfig(): CameraConfig {
        return CameraConfig.DEFAULT
    }

    @Provides
    @Singleton
    fun provideKeyboardFrameAnalyzer(): KeyboardFrameAnalyzer {
        return KeyboardFrameAnalyzer()
    }
}
