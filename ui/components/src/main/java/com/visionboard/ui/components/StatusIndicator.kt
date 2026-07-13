package com.visionboard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryUnknown
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class ConnectionState {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    ERROR
}

@Composable
fun StatusIndicator(
    connectionState: ConnectionState,
    batteryLevel: Int?,
    fps: Float,
    latencyMs: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BluetoothStatus(state = connectionState)

        Spacer(modifier = Modifier.width(4.dp))

        BatteryIndicator(level = batteryLevel)

        Spacer(modifier = Modifier.width(4.dp))

        MetricDisplay(
            label = "FPS",
            value = "%.0f".format(fps),
            isGood = fps >= 30f
        )

        MetricDisplay(
            label = "LAT",
            value = "%.0fms".format(latencyMs),
            isGood = latencyMs <= 50f
        )
    }
}

@Composable
private fun BluetoothStatus(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (icon, color, label) = when (state) {
        ConnectionState.CONNECTED -> Triple(
            Icons.Filled.BluetoothConnected,
            Color(0xFF66BB6A),
            "Connected"
        )
        ConnectionState.CONNECTING -> Triple(
            Icons.Filled.Bluetooth,
            Color(0xFFFFA726),
            "Connecting"
        )
        ConnectionState.DISCONNECTED -> Triple(
            Icons.Filled.BluetoothDisabled,
            Color(0xFFEF5350),
            "Disconnected"
        )
        ConnectionState.ERROR -> Triple(
            Icons.Filled.BluetoothDisabled,
            Color(0xFFD32F2F),
            "Error"
        )
    }

    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(300),
        label = "bluetooth_color"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(animatedColor)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = icon,
            contentDescription = "Bluetooth: $label",
            tint = animatedColor,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BatteryIndicator(
    level: Int?,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when {
        level == null -> Icons.Filled.BatteryUnknown
        level >= 90 -> Icons.Filled.BatteryFull
        level >= 75 -> Icons.Filled.Battery6Bar
        level >= 60 -> Icons.Filled.Battery5Bar
        level >= 45 -> Icons.Filled.Battery4Bar
        level >= 30 -> Icons.Filled.Battery3Bar
        level >= 15 -> Icons.Filled.Battery2Bar
        else -> Icons.Filled.Battery1Bar
    }

    val color = when {
        level == null -> MaterialTheme.colorScheme.onSurfaceVariant
        level <= 15 -> Color(0xFFEF5350)
        level <= 30 -> Color(0xFFFFA726)
        else -> Color(0xFF66BB6A)
    }

    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(300),
        label = "battery_color"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Battery: ${level ?: "unknown"}%",
            tint = animatedColor,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "${level ?: "--"}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MetricDisplay(
    label: String,
    value: String,
    isGood: Boolean,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = if (isGood) Color(0xFF66BB6A) else Color(0xFFEF5350),
        animationSpec = tween(300),
        label = "metric_color"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
fun CompactStatusBadge(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> Color(0xFF66BB6A)
            ConnectionState.CONNECTING -> Color(0xFFFFA726)
            ConnectionState.DISCONNECTED -> Color(0xFFEF5350)
            ConnectionState.ERROR -> Color(0xFFD32F2F)
        },
        animationSpec = tween(300),
        label = "badge_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val displayAlpha = when (connectionState) {
        ConnectionState.CONNECTED -> 1f
        ConnectionState.CONNECTING -> pulseAlpha
        ConnectionState.DISCONNECTED -> 0.6f
        ConnectionState.ERROR -> pulseAlpha
    }

    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = displayAlpha))
    )
}

@Composable
fun DetailedStatusPanel(
    connectionState: ConnectionState,
    batteryLevel: Int?,
    fps: Float,
    latencyMs: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusRow(
            label = "Connection",
            value = connectionState.name
        )

        StatusRow(
            label = "Battery",
            value = "${batteryLevel ?: "--"}%"
        )

        StatusRow(
            label = "Frame Rate",
            value = "%.1f FPS".format(fps)
        )

        StatusRow(
            label = "Latency",
            value = "%.1f ms".format(latencyMs)
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
