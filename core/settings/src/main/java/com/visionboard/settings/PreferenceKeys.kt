package com.visionboard.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {

    val SENSITIVITY = floatPreferencesKey("sensitivity")

    val DEBOUNCE_MS = longPreferencesKey("debounce_ms")

    val CAMERA_FPS = intPreferencesKey("camera_fps")

    val CAMERA_RESOLUTION = stringPreferencesKey("camera_resolution")

    val DARK_MODE = booleanPreferencesKey("dark_mode")

    val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")

    val GESTURE_ENABLED = booleanPreferencesKey("gesture_enabled")

    val KEYBOARD_LAYOUT = stringPreferencesKey("keyboard_layout")

    val BLUETOOTH_AUTO_RECONNECT = booleanPreferencesKey("bluetooth_auto_reconnect")

    val WIFI_MODE_ENABLED = booleanPreferencesKey("wifi_mode_enabled")

    val CALIBRATION_BACKUP_ENABLED = booleanPreferencesKey("calibration_backup_enabled")

    val SETTINGS_VERSION = intPreferencesKey("settings_version")

    val LAST_MODIFIED = longPreferencesKey("last_modified")
}
