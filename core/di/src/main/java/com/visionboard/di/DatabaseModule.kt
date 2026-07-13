package com.visionboard.di

import android.content.Context
import androidx.room.Room
import com.visionboard.database.VisionBoardDatabase
import com.visionboard.database.dao.CalibrationDao
import com.visionboard.database.dao.StatsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVisionBoardDatabase(
        @ApplicationContext context: Context,
    ): VisionBoardDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            VisionBoardDatabase::class.java,
            VisionBoardDatabase.DATABASE_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideCalibrationDao(
        database: VisionBoardDatabase,
    ): CalibrationDao {
        return database.calibrationDao()
    }

    @Provides
    @Singleton
    fun provideStatsDao(
        database: VisionBoardDatabase,
    ): StatsDao {
        return database.statsDao()
    }
}
