package com.visionboard.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionboard.common.models.AppSettings
import com.visionboard.common.models.CameraResolution
import com.visionboard.settings.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val sensitivity: Float = 0.7f,
    val debounceTime: Long = 50L,
    val cameraFps: Int = 30,
    val cameraResolution: CameraResolution = CameraResolution.MEDIUM,
    val darkMode: Boolean = true,
    val developerMode: Boolean = false,
    val gestureEnabled: Boolean = true,
    val autoReconnect: Boolean = true,
    val wifiModeEnabled: Boolean = false,
    val wifiPort: Int = 8765,
    val keyboardLayout: String = "qwerty_us",
    val serverOnline: Boolean = false,
    val connectedClients: Int = 0,
    val showOverlays: Boolean = false,
    val calibrationLastExported: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = settingsManager.observeSettings().first()
            _uiState.value = SettingsUiState(
                sensitivity = settings.sensitivity,
                debounceTime = settings.debounceMs,
                cameraFps = settings.cameraFps,
                cameraResolution = settings.cameraResolution,
                darkMode = settings.darkMode,
                developerMode = settings.developerMode,
                gestureEnabled = settings.gestureEnabled,
                autoReconnect = settings.bluetoothAutoReconnect,
                wifiModeEnabled = settings.wifiModeEnabled,
                keyboardLayout = settings.keyboardLayout,
            )
        }
    }

    fun updateSensitivity(value: Float) {
        settingsManager.updateSensitivity(value)
        _uiState.update { it.copy(sensitivity = value) }
    }

    fun updateDebounceTime(value: Long) {
        settingsManager.updateDebounceTime(value)
        _uiState.update { it.copy(debounceTime = value) }
    }

    fun updateCameraFps(fps: Int) {
        settingsManager.updateCameraFps(fps)
        _uiState.update { it.copy(cameraFps = fps) }
    }

    fun updateResolution(resolution: CameraResolution) {
        settingsManager.updateCameraResolution(resolution)
        _uiState.update { it.copy(cameraResolution = resolution) }
    }

    fun toggleDarkMode() {
        settingsManager.toggleDarkMode()
        _uiState.update { it.copy(darkMode = !it.darkMode) }
    }

    fun toggleDeveloperMode() {
        settingsManager.toggleDeveloperMode()
        _uiState.update { it.copy(developerMode = !it.developerMode) }
    }

    fun toggleGestures() {
        val newValue = !_uiState.value.gestureEnabled
        settingsManager.toggleGestures(newValue)
        _uiState.update { it.copy(gestureEnabled = newValue) }
    }

    fun toggleAutoReconnect() {
        val newValue = !_uiState.value.autoReconnect
        settingsManager.toggleAutoReconnect(newValue)
        _uiState.update { it.copy(autoReconnect = newValue) }
    }

    fun toggleWifiMode() {
        val newValue = !_uiState.value.wifiModeEnabled
        settingsManager.toggleWifiMode(newValue)
        _uiState.update { it.copy(wifiModeEnabled = newValue) }
    }

    fun updateKeyboardLayout(layout: String) {
        settingsManager.updateKeyboardLayout(layout)
        _uiState.update { it.copy(keyboardLayout = layout) }
    }

    fun resetCalibration() {
        viewModelScope.launch {
            settingsManager.resetAllSettings()
            loadSettings()
        }
    }

    fun exportCalibration() {
        val json = settingsManager.exportSettings()
        _uiState.update {
            it.copy(calibrationLastExported = "Exported at ${java.text.SimpleDateFormat(
                "HH:mm:ss", java.util.Locale.US
            ).format(java.util.Date())}")
        }
    }

    fun importCalibration() {
        viewModelScope.launch {
            val current = settingsManager.getSettingsOnce()
            val json = kotlinx.serialization.json.Json {
                encodeDefaults = true
            }.encodeToString(current)
            val success = settingsManager.importSettings(json)
            if (success) {
                loadSettings()
            }
        }
    }
}
