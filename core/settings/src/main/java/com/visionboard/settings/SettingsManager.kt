package com.visionboard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.visionboard.common.models.AppSettings
import com.visionboard.common.models.CameraResolution
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "visionboard_settings"
)

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dataStore: DataStore<Preferences> = context.dataStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun observeSettings(): Flow<AppSettings> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                mapPreferencesToSettings(preferences)
            }
    }

    suspend fun getSettingsOnce(): AppSettings {
        return try {
            val preferences = dataStore.data.first()
            mapPreferencesToSettings(preferences)
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun updateSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(0.1f, 1.0f)
        scope.launch {
            dataStore.edit { preferences ->
                preferences[PreferenceKeys.SENSITIVITY] = clamped
                preferences[PreferenceKeys.LAST_MODIFIED] = System.currentTimeMillis()
            }
        }
    }

    fun updateDebounceTime(debounceMs: Long) {
        val clamped = debounceMs.coerceIn(10L, 500L)
        scope.launch {
            dataStore.edit { preferences ->
                preferences[PreferenceKeys.DEBOUNCE_MS] = clamped
                preferences[PreferenceKeys.LAST_MODIFIED] = System.currentTimeMillis()
            }
        }
    }

    fun updateCameraFps(fps: Int) {
        val clamped = fps.coerceIn(15, 60)
        scope.launch {
            dataStore.edit { preferences ->
                preferences[PreferenceKeys.CAMERA_FPS] = clamped
                preferences[PreferenceKeys.LAST_MODIFIED] = System.currentTimeMillis()
            }
        }
    }

    fun updateCameraResolution(resolution: CameraResolution) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[PreferenceKeys.CAMERA_RESOLUTION] = resolution.name
                preferences[PreferenceKeys.LAST_MODIFIED] = System.currentTimeMillis()
            }
        }
    }

    fun toggleDarkMode() {
        scope.launch {
            dataStore.edit { preferences ->
                val current = preferences[PreferenceKeys.DARK_MODE] ?: true
                preferences[PreferenceKeys.DARK_MODE] = !current
                preferences[PreferenceKeys.LAST_MODIFIED] = System.currentTimeMillis()
            }
        }
    }

    fun toggleDeveloperMode() {
        scope.launch {
            dataStore.edit { preferences ->
                val current = preferences[PreferenceKeys.DEVELOPER_MODE] ?: false
                preferences[PreferenceKeys.DEVELOPER_MODE] = !current
                preferences[PreferenceKeys.LAST_MODIFIED] = System.currentTimeMillis()
            }
        }
    }

    fun toggleGestures(enabled: Boolean) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[PreferenceKeys.GESTURE_ENABLED] = enabled
                preferences[PreferenceKeys.LAST_MODIFIED] = System.currentTimeMillis()
            }
        }
    }

    fun updateKeyboardLayout(layout: String) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[PreferenceKeys.KEYBOARD_LAYOUT] = layout
                preferences[PreferenceKeys.LAST_MODIFIED] = System.currentTimeMillis()
            }
        }
    }

    fun toggleAutoReconnect(enabled: Boolean) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[PreferenceKeys.BLUETOOTH_AUTO_RECONNECT] = enabled
                preferences[PreferenceKeys.LAST_MODIFIED] = System.currentTimeMillis()
            }
        }
    }

    fun toggleWifiMode(enabled: Boolean) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[PreferenceKeys.WIFI_MODE_ENABLED] = enabled
                preferences[PreferenceKeys.LAST_MODIFIED] = System.currentTimeMillis()
            }
        }
    }

    fun resetAllSettings() {
        scope.launch {
            dataStore.edit { preferences ->
                preferences.clear()
            }
        }
    }

    fun exportSettings(): String {
        return try {
            val settings = runBlocking { getSettingsOnce() }
            json.encodeToString(settings)
        } catch (e: Exception) {
            json.encodeToString(AppSettings())
        }
    }

    fun importSettings(jsonString: String): Boolean {
        return try {
            val settings = json.decodeFromString<AppSettings>(jsonString).validate()
            scope.launch {
                dataStore.edit { preferences ->
                    preferences[PreferenceKeys.SENSITIVITY] = settings.sensitivity
                    preferences[PreferenceKeys.DEBOUNCE_MS] = settings.debounceMs
                    preferences[PreferenceKeys.CAMERA_FPS] = settings.cameraFps
                    preferences[PreferenceKeys.CAMERA_RESOLUTION] = settings.cameraResolution.name
                    preferences[PreferenceKeys.DARK_MODE] = settings.darkMode
                    preferences[PreferenceKeys.DEVELOPER_MODE] = settings.developerMode
                    preferences[PreferenceKeys.GESTURE_ENABLED] = settings.gestureEnabled
                    preferences[PreferenceKeys.KEYBOARD_LAYOUT] = settings.keyboardLayout
                    preferences[PreferenceKeys.BLUETOOTH_AUTO_RECONNECT] = settings.bluetoothAutoReconnect
                    preferences[PreferenceKeys.WIFI_MODE_ENABLED] = settings.wifiModeEnabled
                    preferences[PreferenceKeys.CALIBRATION_BACKUP_ENABLED] = settings.calibrationBackupEnabled
                    preferences[PreferenceKeys.LAST_MODIFIED] = System.currentTimeMillis()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun mapPreferencesToSettings(preferences: Preferences): AppSettings {
        val sensitivity = preferences[PreferenceKeys.SENSITIVITY] ?: 0.7f
        val debounceMs = preferences[PreferenceKeys.DEBOUNCE_MS] ?: 50L
        val cameraFps = preferences[PreferenceKeys.CAMERA_FPS] ?: 30
        val cameraResolutionName = preferences[PreferenceKeys.CAMERA_RESOLUTION] ?: "MEDIUM"
        val darkMode = preferences[PreferenceKeys.DARK_MODE] ?: true
        val developerMode = preferences[PreferenceKeys.DEVELOPER_MODE] ?: false
        val gestureEnabled = preferences[PreferenceKeys.GESTURE_ENABLED] ?: true
        val keyboardLayout = preferences[PreferenceKeys.KEYBOARD_LAYOUT] ?: "qwerty_us"
        val bluetoothAutoReconnect = preferences[PreferenceKeys.BLUETOOTH_AUTO_RECONNECT] ?: true
        val wifiModeEnabled = preferences[PreferenceKeys.WIFI_MODE_ENABLED] ?: false
        val calibrationBackupEnabled = preferences[PreferenceKeys.CALIBRATION_BACKUP_ENABLED] ?: true

        val cameraResolution = try {
            CameraResolution.valueOf(cameraResolutionName)
        } catch (_: IllegalArgumentException) {
            CameraResolution.MEDIUM
        }

        return AppSettings(
            sensitivity = sensitivity,
            debounceMs = debounceMs,
            cameraFps = cameraFps,
            cameraResolution = cameraResolution,
            darkMode = darkMode,
            developerMode = developerMode,
            gestureEnabled = gestureEnabled,
            keyboardLayout = keyboardLayout,
            bluetoothAutoReconnect = bluetoothAutoReconnect,
            wifiModeEnabled = wifiModeEnabled,
            calibrationBackupEnabled = calibrationBackupEnabled,
        ).validate()
    }

    companion object {
        private fun <T> runBlocking(block: suspend () -> T): T {
            return kotlinx.coroutines.runBlocking {
                block()
            }
        }
    }
}
