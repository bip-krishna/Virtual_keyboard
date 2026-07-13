package com.visionboard.domain.repository

import com.visionboard.domain.model.AppSettingsDomain
import kotlinx.coroutines.flow.Flow

interface SettingsRepositoryInterface {
    fun getSettings(): Flow<AppSettingsDomain>
    suspend fun updateSettings(settings: AppSettingsDomain)
    suspend fun resetSettings()
}
