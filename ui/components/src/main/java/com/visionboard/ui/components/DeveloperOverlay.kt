package com.visionboard.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionboard.ui.theme.LocalIsDeveloperMode

data class DeveloperMetrics(
    val frameRate: Float = 0f,
    val latencyMs: Float = 0f,
    val cpuUsage: Float = 0f,
    val memoryUsageMb: Float = 0f,
    val detectedKeys: List<String> = emptyList(),
    val fingerPositions: Map<Int, Pair<Float, Float>> = emptyMap(),
    val homographyConfidence: Float = 0f,
    val bluetoothStatus: String = "Disconnected",
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun DeveloperOverlay(
    metrics: DeveloperMetrics,
    modifier: Modifier = Modifier
) {
    val isDeveloperMode = LocalIsDeveloperMode.current

    if (!isDeveloperMode) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(12.dp)
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeveloperOverlayHeader()

            Spacer(modifier = Modifier.height(4.dp))

            MetricSection(title = "Performance") {
                MetricRow(label = "FPS", value = "%.1f".format(metrics.frameRate))
                MetricRow(label = "Latency", value = "%.1f ms".format(metrics.latencyMs))
                MetricRow(label = "CPU", value = "%.1f%%".format(metrics.cpuUsage))
                MetricRow(
                    label = "Memory",
                    value = "%.1f MB".format(metrics.memoryUsageMb)
                )
            }

            MetricSection(title = "Detection") {
                MetricRow(
                    label = "Keys",
                    value = if (metrics.detectedKeys.isEmpty()) "None"
                    else metrics.detectedKeys.joinToString(", ")
                )
                MetricRow(
                    label = "Confidence",
                    value = "%.2f".format(metrics.homographyConfidence)
                )
            }

            MetricSection(title = "Fingers") {
                if (metrics.fingerPositions.isEmpty()) {
                    MetricRow(label = "Status", value = "No fingers detected")
                } else {
                    metrics.fingerPositions.forEach { (id, pos) ->
                        MetricRow(
                            label = "F$id",
                            value = "(%.0f, %.0f)".format(pos.first, pos.second)
                        )
                    }
                }
            }

            MetricSection(title = "Connection") {
                MetricRow(label = "Bluetooth", value = metrics.bluetoothStatus)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "t: ${metrics.timestamp}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                ),
                color = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun DeveloperOverlayHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "DEV OVERLAY",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = Color(0xFF4FC3F7)
        )

        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF66BB6A))
        )
    }
}

@Composable
private fun MetricSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            ),
            color = Color(0xFFFFB74D)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = Color.White.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
fun MiniDeveloperOverlay(
    metrics: DeveloperMetrics,
    modifier: Modifier = Modifier
) {
    val isDeveloperMode = LocalIsDeveloperMode.current

    if (!isDeveloperMode) return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniMetric(
            label = "F",
            value = "%.0f".format(metrics.frameRate),
            color = if (metrics.frameRate >= 30f) Color(0xFF66BB6A) else Color(0xFFEF5350)
        )

        MiniMetric(
            label = "L",
            value = "%.0f".format(metrics.latencyMs),
            color = if (metrics.latencyMs <= 50f) Color(0xFF66BB6A) else Color(0xFFEF5350)
        )

        MiniMetric(
            label = "C",
            value = "%.0f".format(metrics.cpuUsage),
            color = if (metrics.cpuUsage <= 50f) Color(0xFF66BB6A) else Color(0xFFFFA726)
        )

        MiniMetric(
            label = "M",
            value = "%.0f".format(metrics.memoryUsageMb),
            color = Color(0xFF4FC3F7)
        )
    }
}

@Composable
private fun MiniMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            ),
            color = color.copy(alpha = 0.7f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}
