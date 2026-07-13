package com.visionboard.di

import com.visionboard.geometry.calibration.CalibrationEngine
import com.visionboard.geometry.feature.FeatureMatcher
import com.visionboard.geometry.homography.HomographyEngine
import com.visionboard.geometry.perspective.PerspectiveTransformer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeometryModule {

    @Provides
    @Singleton
    fun provideHomographyEngine(): HomographyEngine {
        return HomographyEngine()
    }

    @Provides
    @Singleton
    fun providePerspectiveTransformer(): PerspectiveTransformer {
        return PerspectiveTransformer()
    }

    @Provides
    @Singleton
    fun provideCalibrationEngine(): CalibrationEngine {
        return CalibrationEngine()
    }

    @Provides
    @Singleton
    fun provideFeatureMatcher(): FeatureMatcher {
        return FeatureMatcher()
    }
}
