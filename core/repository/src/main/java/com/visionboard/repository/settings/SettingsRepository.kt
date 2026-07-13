package com.visionboard.repository.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.visionboard.domain.model.AppSettingsDomain
import com.visionboard.domain.repository.SettingsRepositoryInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "visionboard_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepositoryInterface {

    private val dataStore = context.dataStore

    override fun getSettings(): Flow<AppSettingsDomain> {
        return dataStore.data
            .catch { emit(Preferences.EMPTY) }
            .map { prefs ->
                AppSettingsDomain(
                    sensitivity = prefs[KEY_SENSITIVITY] ?: DEFAULT_SENSITIVITY,
                    debounceMs = prefs[KEY_DEBOUNCE_MS] ?: DEFAULT_DEBOUNCE_MS,
                    cameraFps = prefs[KEY_CAMERA_FPS] ?: DEFAULT_CAMERA_FPS,
                    resolution = prefs[KEY_RESOLUTION] ?: DEFAULT_RESOLUTION,
                    darkMode = prefs[KEY_DARK_MODE] ?: DEFAULT_DARK_MODE,
                    developerMode = prefs[KEY_DEVELOPER_MODE] ?: DEFAULT_DEVELOPER_MODE,
                    gestureEnabled = prefs[KEY_GESTURE_ENABLED] ?: DEFAULT_GESTURE_ENABLED,
                    keyboardLayout = prefs[KEY_KEYBOARD_LAYOUT] ?: DEFAULT_KEYBOARD_LAYOUT,
                    autoReconnect = prefs[KEY_AUTO_RECONNECT] ?: DEFAULT_AUTO_RECONNECT,
                    wifiMode = prefs[KEY_WIFI_MODE] ?: DEFAULT_WIFI_MODE,
                )
            }
    }

    override suspend fun updateSettings(settings: AppSettingsDomain) {
        dataStore.edit { prefs ->
            prefs[KEY_SENSITIVITY] = settings.sensitivity
            prefs[KEY_DEBOUNCE_MS] = settings.debounceMs
            prefs[KEY_CAMERA_FPS] = settings.cameraFps
            prefs[KEY_RESOLUTION] = settings.resolution
            prefs[KEY_DARK_MODE] = settings.darkMode
            prefs[KEY_DEVELOPER_MODE] = settings.developerMode
            prefs[KEY_GESTURE_ENABLED] = settings.gestureEnabled
            prefs[KEY_KEYBOARD_LAYOUT] = settings.keyboardLayout
            prefs[KEY_AUTO_RECONNECT] = settings.autoReconnect
            prefs[KEY_WIFI_MODE] = settings.wifiMode
        }
    }

    override suspend fun resetSettings() {
        dataStore.edit { prefs ->
            prefs[KEY_SENSITIVITY] = DEFAULT_SENSITIVITY
            prefs[KEY_DEBOUNCE_MS] = DEFAULT_DEBOUNCE_MS
            prefs[KEY_CAMERA_FPS] = DEFAULT_CAMERA_FPS
            prefs[KEY_RESOLUTION] = DEFAULT_RESOLUTION
            prefs[KEY_DARK_MODE] = DEFAULT_DARK_MODE
            prefs[KEY_DEVELOPER_MODE] = DEFAULT_DEVELOPER_MODE
            prefs[KEY_GESTURE_ENABLED] = DEFAULT_GESTURE_ENABLED
            prefs[KEY_KEYBOARD_LAYOUT] = DEFAULT_KEYBOARD_LAYOUT
            prefs[KEY_AUTO_RECONNECT] = DEFAULT_AUTO_RECONNECT
            prefs[KEY_WIFI_MODE] = DEFAULT_WIFI_MODE
        }
    }

    fun <T> getSpecificSetting(key: Preferences.Key<T>): Flow<T?> {
        return dataStore.data
            .catch { emit(Preferences.EMPTY) }
            .map { prefs -> prefs[key] }
    }

    companion object {
        val KEY_SENSITIVITY = floatPreferencesKey("sensitivity")
        val KEY_DEBOUNCE_MS = longPreferencesKey("debounce_ms")
        val KEY_CAMERA_FPS = intPreferencesKey("camera_fps")
        val KEY_RESOLUTION = stringPreferencesKey("resolution")
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        val KEY_DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val KEY_GESTURE_ENABLED = booleanPreferencesKey("gesture_enabled")
        val KEY_KEYBOARD_LAYOUT = stringPreferencesKey("keyboard_layout")
        val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val KEY_WIFI_MODE = booleanPreferencesKey("wifi_mode")

        const val DEFAULT_SENSITIVITY = 0.7f
        const val DEFAULT_DEBOUNCE_MS = 50L
        const val DEFAULT_CAMERA_FPS = 30
        const val DEFAULT_RESOLUTION = "MEDIUM"
        const val DEFAULT_DARK_MODE = true
        const val DEFAULT_DEVELOPER_MODE = false
        const val DEFAULT_GESTURE_ENABLED = true
        const val DEFAULT_KEYBOARD_LAYOUT = "qwerty_us"
        const val DEFAULT_AUTO_RECONNECT = true
        const val DEFAULT_WIFI_MODE = false
    }
}
