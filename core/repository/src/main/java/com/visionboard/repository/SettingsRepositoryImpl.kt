package com.visionboard.repository

import com.visionboard.domain.model.AppSettingsDomain
import com.visionboard.domain.repository.SettingsRepositoryInterface
import com.visionboard.settings.SettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsManager: SettingsManager,
) : SettingsRepositoryInterface {

    override fun getSettings(): Flow<AppSettingsDomain> {
        return settingsManager.observeSettings().map { appSettings ->
            AppSettingsDomain(
                sensitivity = appSettings.sensitivity,
                debounceMs = appSettings.debounceMs,
                cameraFps = appSettings.cameraFps,
                resolution = appSettings.cameraResolution.name,
                darkMode = appSettings.darkMode,
                developerMode = appSettings.developerMode,
                gestureEnabled = appSettings.gestureEnabled,
                keyboardLayout = appSettings.keyboardLayout,
                autoReconnect = appSettings.bluetoothAutoReconnect,
                wifiMode = appSettings.wifiModeEnabled,
            )
        }
    }

    override suspend fun updateSettings(settings: AppSettingsDomain) {
        settingsManager.updateSensitivity(settings.sensitivity)
        settingsManager.updateDebounceTime(settings.debounceMs)
        settingsManager.updateCameraFps(settings.cameraFps)
        settingsManager.updateKeyboardLayout(settings.keyboardLayout)
        settingsManager.toggleAutoReconnect(settings.autoReconnect)
        settingsManager.toggleWifiMode(settings.wifiMode)
        if (settings.darkMode) settingsManager.toggleDarkMode()
        if (settings.developerMode) settingsManager.toggleDeveloperMode()
        if (settings.gestureEnabled) settingsManager.toggleGestures(true)
    }

    override suspend fun resetSettings() {
        settingsManager.resetAllSettings()
    }
}
