package com.visionboard.ui.splash

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionboard.database.dao.CalibrationDao
import com.visionboard.settings.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NavigationDestination {
    ONBOARDING,
    CALIBRATION,
    MAIN
}

sealed class SplashState {
    data object Loading : SplashState()
    data class Ready(val navigateTo: NavigationDestination) : SplashState()
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val calibrationDao: CalibrationDao
) : ViewModel() {

    private val _state = MutableStateFlow<SplashState>(SplashState.Loading)
    val state: StateFlow<SplashState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val destination = determineNavigationDestination()
            delay(2200)
            _state.value = SplashState.Ready(navigateTo = destination)
        }
    }

    private suspend fun determineNavigationDestination(): NavigationDestination {
        val isFirstLaunch = isFirstLaunch()
        if (isFirstLaunch) {
            markFirstLaunchDone()
            return NavigationDestination.ONBOARDING
        }

        val hasCalibration = calibrationDao.getActiveCalibration() != null
        if (!hasCalibration) {
            return NavigationDestination.CALIBRATION
        }

        return NavigationDestination.MAIN
    }

    private suspend fun isFirstLaunch(): Boolean {
        return try {
            val prefs = context.getSharedPreferences("visionboard_prefs", Context.MODE_PRIVATE)
            !prefs.contains(KEY_ONBOARDING_DONE)
        } catch (_: Exception) {
            true
        }
    }

    private fun markFirstLaunchDone() {
        val prefs = context.getSharedPreferences("visionboard_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    private val hasBluetoothPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val KEY_ONBOARDING_DONE = "onboarding_completed"
    }
}
