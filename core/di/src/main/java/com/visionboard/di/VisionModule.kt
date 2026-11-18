package com.visionboard.di

import com.visionboard.vision.detector.FeatureDetector
import com.visionboard.vision.detector.KeyboardDetector
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VisionModule {

    @Provides
    @Singleton
    fun provideFeatureDetector(): FeatureDetector {
        return FeatureDetector()
    }

    @Provides
    @Singleton
    fun provideKeyboardDetector(
        featureDetector: Lazy<FeatureDetector>,
    ): KeyboardDetector {
        return KeyboardDetector(featureDetector)
    }
}
