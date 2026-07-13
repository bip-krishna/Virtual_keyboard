package com.visionboard.di

import com.visionboard.domain.repository.CalibrationRepositoryInterface
import com.visionboard.domain.repository.SettingsRepositoryInterface
import com.visionboard.repository.CalibrationRepositoryImpl
import com.visionboard.repository.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCalibrationRepository(
        implementation: CalibrationRepositoryImpl,
    ): CalibrationRepositoryInterface

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        implementation: SettingsRepositoryImpl,
    ): SettingsRepositoryInterface
}
