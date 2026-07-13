package com.visionboard.di

import com.visionboard.tracking.finger.FingerTracker
import com.visionboard.tracking.gesture.GestureDetector
import com.visionboard.tracking.touch.TouchDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TrackingModule {

    @Provides
    @Singleton
    fun provideFingerTracker(): FingerTracker {
        return FingerTracker()
    }

    @Provides
    @Singleton
    fun provideTouchDetector(): TouchDetector {
        return TouchDetector()
    }

    @Provides
    @Singleton
    fun provideGestureDetector(): GestureDetector {
        return GestureDetector()
    }
}
