package com.visionboard.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.visionboard.common.models.CameraResolution
import com.visionboard.ui.components.SettingsDropdownItem
import com.visionboard.ui.components.SettingsSectionHeader
import com.visionboard.ui.components.SettingsSliderItem
import com.visionboard.ui.components.SettingsSwitchItem
import com.visionboard.ui.components.SettingsItem
import com.visionboard.ui.theme.DeepBlueLight
import com.visionboard.ui.theme.ErrorRed
import com.visionboard.ui.theme.OnSurfaceDark
import com.visionboard.ui.theme.SurfaceDark
import com.visionboard.ui.theme.SurfaceDarkContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceDark,
                ),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Typing Section ────────────────────────────────────────────────────
        item {
            SettingsSectionHeader(
                title = "TYPING",
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            SettingsSliderItem(
                title = "Sensitivity",
                subtitle = "Touch detection sensitivity",
                value = state.sensitivity,
                onValueChange = viewModel::updateSensitivity,
                icon = Icons.Filled.Sensors,
                valueRange = 0.1f..1.0f,
                steps = 8,
                valueLabel = { "%.0f%%".format(it * 100) },
            )
        }

        item {
            SettingsSliderItem(
                title = "Debounce Time",
                subtitle = "Minimum interval between key presses",
                value = state.debounceTime.toFloat(),
                onValueChange = { viewModel.updateDebounceTime(it.toLong()) },
                icon = Icons.Filled.Accessibility,
                valueRange = 10f..500f,
                steps = 48,
                valueLabel = { "%.0f ms".format(it) },
            )
        }

        // ── Camera Section ────────────────────────────────────────────────────
        item {
            SettingsSectionHeader(title = "CAMERA")
        }

        item {
            SettingsDropdownItem(
                title = "FPS",
                subtitle = "Camera capture framerate",
                selectedValue = "${state.cameraFps} FPS",
                options = listOf("15 FPS", "30 FPS", "60 FPS"),
                icon = Icons.Filled.Videocam,
                onOptionSelected = { option ->
                    val fps = option.replace(" FPS", "").toIntOrNull() ?: 30
                    viewModel.updateCameraFps(fps)
                },
            )
        }

        item {
            SettingsDropdownItem(
                title = "Resolution",
                subtitle = "Camera capture resolution",
                selectedValue = state.cameraResolution.label,
                options = CameraResolution.entries.map { it.label },
                icon = Icons.Filled.CameraAlt,
                onOptionSelected = { option ->
                    CameraResolution.entries.find { it.label == option }?.let {
                        viewModel.updateResolution(it)
                    }
                },
            )
        }

        // ── Bluetooth Section ─────────────────────────────────────────────────
        item {
            SettingsSectionHeader(title = "BLUETOOTH")
        }

        item {
            SettingsSwitchItem(
                title = "Auto-reconnect",
                subtitle = "Automatically reconnect to paired devices",
                checked = state.autoReconnect,
                onCheckedChange = { viewModel.toggleAutoReconnect() },
                icon = Icons.Filled.Bluetooth,
            )
        }

        item {
            SettingsItem(
                title = "Reconnect",
                subtitle = "Force reconnection to last paired device",
                icon = Icons.Filled.Refresh,
                onClick = { viewModel.toggleAutoReconnect() },
            )
        }

        // ── Connectivity Section ──────────────────────────────────────────────
        item {
            SettingsSectionHeader(title = "CONNECTIVITY")
        }

        item {
            var showPortInput by remember { mutableStateOf(false) }

            SettingsSwitchItem(
                title = "Wi-Fi Mode",
                subtitle = if (state.wifiModeEnabled) "Connected (port ${state.wifiPort})" else "Use Wi-Fi instead of Bluetooth",
                checked = state.wifiModeEnabled,
                onCheckedChange = { viewModel.toggleWifiMode() },
                icon = Icons.Filled.Wifi,
            )

            AnimatedVisibility(
                visible = state.wifiModeEnabled,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 52.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.wifiPort.toString(),
                        onValueChange = { },
                        label = { Text("Port") },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        enabled = false,
                    )

                    Text(
                        text = if (state.serverOnline) "Server: Online" else "Server: Offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.serverOnline) Color(0xFF66BB6A) else OnSurfaceDark.copy(alpha = 0.5f),
                    )
                }
            }
        }

        // ── Gestures Section ──────────────────────────────────────────────────
        item {
            SettingsSectionHeader(title = "GESTURES")
        }

        item {
            SettingsSwitchItem(
                title = "Gesture Shortcuts",
                subtitle = "Enable gesture-based shortcuts (pinch, swipe)",
                checked = state.gestureEnabled,
                onCheckedChange = { viewModel.toggleGestures() },
                icon = Icons.Filled.Accessibility,
            )
        }

        // ── Appearance Section ────────────────────────────────────────────────
        item {
            SettingsSectionHeader(title = "APPEARANCE")
        }

        item {
            SettingsSwitchItem(
                title = "Dark Mode",
                subtitle = if (state.darkMode) "Dark theme active" else "Light theme active",
                checked = state.darkMode,
                onCheckedChange = { viewModel.toggleDarkMode() },
                icon = Icons.Filled.Brightness6,
            )
        }

        // ── Keyboard Section ──────────────────────────────────────────────────
        item {
            SettingsSectionHeader(title = "KEYBOARD")
        }

        item {
            SettingsDropdownItem(
                title = "Layout",
                subtitle = "Active keyboard layout",
                selectedValue = state.keyboardLayout.replace("_", " ").uppercase(),
                options = listOf("QWERTY US", "QWERTY UK", "AZERTY FR", "QWERTZ DE"),
                icon = Icons.Filled.Keyboard,
                onOptionSelected = { option ->
                    val layout = option.lowercase().replace(" ", "_")
                    viewModel.updateKeyboardLayout(layout)
                },
            )
        }

        // ── Data Section ──────────────────────────────────────────────────────
        item {
            SettingsSectionHeader(title = "DATA")
        }

        item {
            SettingsItem(
                title = "Export Calibration",
                subtitle = state.calibrationLastExported.ifEmpty { "Save calibration data to file" },
                icon = Icons.Filled.Upload,
                onClick = viewModel::exportCalibration,
            )
        }

        item {
            SettingsItem(
                title = "Import Calibration",
                subtitle = "Load calibration data from file",
                icon = Icons.Filled.Download,
                onClick = viewModel::importCalibration,
            )
        }

        item {
            SettingsItem(
                title = "Reset Calibration",
                subtitle = "Clear all calibration data",
                icon = Icons.Filled.RestartAlt,
                onClick = viewModel::resetCalibration,
            )
        }

        // ── Developer Section ─────────────────────────────────────────────────
        item {
            SettingsSectionHeader(title = "DEVELOPER")
        }

        item {
            SettingsSwitchItem(
                title = "Developer Mode",
                subtitle = "Enable debug overlays and developer tools",
                checked = state.developerMode,
                onCheckedChange = { viewModel.toggleDeveloperMode() },
                icon = Icons.Filled.Code,
            )
        }

        item {
            SettingsSwitchItem(
                title = "Show Overlays",
                subtitle = "Display debug overlays on camera feed",
                checked = state.showOverlays,
                onCheckedChange = { viewModel.toggleDeveloperMode() },
                icon = Icons.Filled.DataUsage,
            )
        }

        // ── About Section ─────────────────────────────────────────────────────
        item {
            SettingsSectionHeader(title = "ABOUT")
        }

        item {
            SettingsItem(
                title = "VisionBoard",
                subtitle = "Version 1.0.0",
                icon = Icons.Filled.Info,
            )
        }

        item {
            SettingsItem(
                title = "License",
                subtitle = "MIT License - Open Source",
                icon = Icons.Filled.Info,
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
