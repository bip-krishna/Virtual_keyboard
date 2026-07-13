package com.visionboard.ui.developer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HandGesture
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.visionboard.ui.theme.DeepBlueLight
import com.visionboard.ui.theme.ErrorRed
import com.visionboard.ui.theme.FingerPosition
import com.visionboard.ui.theme.KeyPress
import com.visionboard.ui.theme.OnSurfaceDark
import com.visionboard.ui.theme.SurfaceDark
import com.visionboard.ui.theme.SurfaceDarkContainer

@Composable
fun DeveloperScreen(
    viewModel: DeveloperViewModel = hiltViewModel(),
) {
    val state by viewModel.debugState.collectAsState()
    val logs by viewModel.recentLogs.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Developer",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceDark,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Performance Section ───────────────────────────────────────────────
        item {
            ExpandableSection(
                title = "Performance",
                icon = Icons.Filled.Speed,
                initiallyExpanded = true,
            ) { isExpanded ->
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FpsChart(
                            history = state.fpsHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                        )
                        LatencyHistogram(
                            history = state.latencyHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                        )
                        MetricBar(
                            label = "CPU",
                            value = state.cpuUsagePercent,
                            maxValue = 100f,
                            color = DeepBlueLight,
                        )
                        MetricBar(
                            label = "Memory",
                            value = state.memoryUsedMb,
                            maxValue = state.memoryUsedMb + state.memoryAvailableMb,
                            color = FingerPosition,
                            format = "%.0f MB / %.0f MB",
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            MetricChip("Frame Drops", "${state.frameDropCount}")
                            MetricChip("Total Frames", "${state.totalFramesRendered}")
                            MetricChip("Latency", "%.1fms".format(state.averageLatencyMs))
                        }
                    }
                }
            }
        }

        // ── Detection Section ─────────────────────────────────────────────────
        item {
            ExpandableSection(
                title = "Detection",
                icon = Icons.Filled.Keyboard,
            ) { isExpanded ->
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetricRow("Confidence", "%.2f".format(state.detectionConfidence))
                        MetricRow("Corners", "${state.cornersDetected}")
                        MetricRow("Feature Matches", "${state.featureMatchCount}")
                        MetricRow("Homography Det", "%.4f".format(state.homographyDeterminant))
                    }
                }
            }
        }

        // ── Tracking Section ──────────────────────────────────────────────────
        item {
            ExpandableSection(
                title = "Tracking",
                icon = Icons.Filled.HandGesture,
            ) { isExpanded ->
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetricRow("Active Fingers", "${state.activeFingerCount}")

                        if (state.fingerPositions.isNotEmpty()) {
                            state.fingerPositions.forEach { (finger, pos) ->
                                val velocity = state.fingerVelocities[finger] ?: 0f
                                val confidence = state.fingerConfidence[finger] ?: 0f
                                Text(
                                    text = "$finger: (%.0f, %.0f) v=%.0f c=%.2f"
                                        .format(pos.x, pos.y, velocity, confidence),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    ),
                                    color = OnSurfaceDark.copy(alpha = 0.7f),
                                )
                            }
                        } else {
                            MetricRow("Status", "No fingers detected")
                        }
                    }
                }
            }
        }

        // ── Touch Section ─────────────────────────────────────────────────────
        item {
            ExpandableSection(
                title = "Touch",
                icon = Icons.Filled.TouchApp,
            ) { isExpanded ->
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetricRow("Last Event", state.lastTouchEvent.ifEmpty { "None" })
                        MetricRow("Contact Prob", "%.2f".format(state.contactProbability))
                        MetricRow("Debounce State", state.debounceState)
                        MetricRow("Gesture", state.gestureDetected)
                    }
                }
            }
        }

        // ── Bluetooth Section ─────────────────────────────────────────────────
        item {
            ExpandableSection(
                title = "Bluetooth",
                icon = Icons.Filled.Bluetooth,
            ) { isExpanded ->
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetricRow("State", state.bluetoothConnectionState)
                        MetricRow("Device", state.bluetoothDeviceName.ifEmpty { "None" })
                        MetricRow("Reports Sent", "${state.bluetoothReportsSent}")
                        MetricRow("Errors", "${state.bluetoothErrors}")
                    }
                }
            }
        }

        // ── Network Section ───────────────────────────────────────────────────
        item {
            ExpandableSection(
                title = "Network",
                icon = Icons.Filled.NetworkCheck,
            ) { isExpanded ->
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetricRow("Server State", state.serverState)
                        MetricRow("Connected Clients", "${state.connectedClients}")
                        MetricRow("Messages Sent", "${state.networkMessagesSent}")
                    }
                }
            }
        }

        // ── Overlay Toggles ───────────────────────────────────────────────────
        item {
            ExpandableSection(
                title = "Overlay Toggles",
                icon = Icons.Filled.Visibility,
            ) { isExpanded ->
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OverlayToggle(
                            label = "Camera Feed",
                            checked = state.overlayShowCameraFeed,
                            onToggle = { viewModel.toggleOverlay("cameraFeed") },
                        )
                        OverlayToggle(
                            label = "Keyboard Outline",
                            checked = state.overlayShowKeyboardOutline,
                            onToggle = { viewModel.toggleOverlay("keyboardOutline") },
                        )
                        OverlayToggle(
                            label = "Corner Points",
                            checked = state.overlayShowCornerPoints,
                            onToggle = { viewModel.toggleOverlay("cornerPoints") },
                        )
                        OverlayToggle(
                            label = "Homography Grid",
                            checked = state.overlayShowHomographyGrid,
                            onToggle = { viewModel.toggleOverlay("homographyGrid") },
                        )
                        OverlayToggle(
                            label = "Finger Skeleton",
                            checked = state.overlayShowFingerSkeleton,
                            onToggle = { viewModel.toggleOverlay("fingerSkeleton") },
                        )
                        OverlayToggle(
                            label = "Velocity Vectors",
                            checked = state.overlayShowVelocityVectors,
                            onToggle = { viewModel.toggleOverlay("velocityVectors") },
                        )
                        OverlayToggle(
                            label = "Touch Points",
                            checked = state.overlayShowTouchPoints,
                            onToggle = { viewModel.toggleOverlay("touchPoints") },
                        )
                        OverlayToggle(
                            label = "Key Polygons",
                            checked = state.overlayShowKeyPolygons,
                            onToggle = { viewModel.toggleOverlay("keyPolygons") },
                        )
                        OverlayToggle(
                            label = "FPS Counter",
                            checked = state.overlayShowFpsCounter,
                            onToggle = { viewModel.toggleOverlay("fpsCounter") },
                        )
                    }
                }
            }
        }

        // ── Logs Section ──────────────────────────────────────────────────────
        item {
            ExpandableSection(
                title = "Logs",
                icon = Icons.Filled.BugReport,
            ) { isExpanded ->
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { viewModel.exportLogs() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DeepBlueLight,
                                ),
                            ) {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { viewModel.clearLogs() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ErrorRed,
                                ),
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                        if (logs.isEmpty()) {
                            Text(
                                "No log entries",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDark.copy(alpha = 0.4f),
                            )
                        } else {
                            logs.take(10).forEach { entry ->
                                Text(
                                    text = entry.format(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                    ),
                                    color = OnSurfaceDark.copy(alpha = 0.7f),
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    icon: ImageVector,
    initiallyExpanded: Boolean = false,
    content: @Composable (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDarkContainer,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = DeepBlueLight,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceDark,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = OnSurfaceDark.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                )
            }

            content(expanded)
        }
    }
}

@Composable
private fun FpsChart(
    history: List<Float>,
    modifier: Modifier = Modifier,
) {
    val lineColor = DeepBlueLight
    val fillColor = DeepBlueLight.copy(alpha = 0.1f)

    Canvas(modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (history.size < 2) return@Canvas

        val maxFps = 60f
        val stepX = size.width / (history.size - 1).coerceAtLeast(1)
        val baseline = size.height

        val path = Path().apply {
            moveTo(0f, baseline - (history[0] / maxFps) * size.height)
            for (i in 1 until history.size) {
                val x = i * stepX
                val y = baseline - (history[i].coerceIn(0f, maxFps) / maxFps) * size.height
                lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )

        val fillPath = Path().apply {
            addPath(path)
            lineTo((history.size - 1) * stepX, baseline)
            lineTo(0f, baseline)
            close()
        }
        drawPath(path = fillPath, color = fillColor)
    }
}

@Composable
private fun LatencyHistogram(
    history: List<Float>,
    modifier: Modifier = Modifier,
) {
    val barColor = FingerPosition

    Canvas(modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (history.isEmpty()) return@Canvas

        val maxLatency = 100f
        val barWidth = (size.width / history.size).coerceAtLeast(1f)

        history.forEachIndexed { index, value ->
            val barHeight = (value.coerceIn(0f, maxLatency) / maxLatency) * size.height
            drawRect(
                color = barColor.copy(alpha = 0.5f),
                topLeft = Offset(index * barWidth, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(
                    barWidth.coerceAtMost(4f),
                    barHeight,
                ),
            )
        }
    }
}

@Composable
private fun MetricBar(
    label: String,
    value: Float,
    maxValue: Float,
    color: Color,
    format: String = "%.1f%%",
) {
    val progress by animateFloatAsState(
        targetValue = if (maxValue > 0) (value / maxValue).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(300),
        label = "metricBar",
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = OnSurfaceDark.copy(alpha = 0.7f),
                ),
            )
            Text(
                text = if (format.contains("%.0f MB"))
                    "%.0f MB / %.0f MB".format(value, maxValue)
                else
                    "%.1f%%".format(value),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = OnSurfaceDark.copy(alpha = 0.9f),
                ),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = OnSurfaceDark.copy(alpha = 0.1f),
        )
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(OnSurfaceDark.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceDark.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = OnSurfaceDark.copy(alpha = 0.6f),
            ),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = OnSurfaceDark.copy(alpha = 0.9f),
            ),
        )
    }
}

@Composable
private fun OverlayToggle(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (checked) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = null,
                tint = if (checked) DeepBlueLight else OnSurfaceDark.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (checked) OnSurfaceDark else OnSurfaceDark.copy(alpha = 0.4f),
            )
        }

        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (checked) KeyPress else OnSurfaceDark.copy(alpha = 0.15f)
                ),
        )
    }
}
