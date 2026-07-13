package com.visionboard.ui.typing

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraOff
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visionboard.ui.theme.DeepBlueDark
import com.visionboard.ui.theme.FingerPosition
import com.visionboard.ui.theme.KeyPress
import com.visionboard.ui.theme.KeyboardHighlight
import com.visionboard.ui.theme.SurfaceDark
import com.visionboard.ui.theme.SurfaceDarkContainer
import com.visionboard.ui.theme.VelocityVector
import com.visionboard.ui.theme.HomographyGrid
import com.visionboard.ui.theme.ErrorRed
import java.util.concurrent.Executors

@Composable
fun TypingScreen(
    viewModel: TypingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by viewModel.typingStats.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(SurfaceDark)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Connection Banner ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.showConnectionBanner && state.isTypingActive,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.requestBluetoothPairing() },
                    color = ErrorRed,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Tap to pair Bluetooth keyboard",
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                        }
                        TextButton(onClick = { viewModel.dismissConnectionBanner() }) {
                            Text("Dismiss", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Camera Preview + Overlay (~70%) ──────────────────────────────
            Box(
                modifier = Modifier.weight(0.7f).fillMaxWidth()
            ) {
                if (state.cameraActive) {
                    CameraPreview(modifier = Modifier.fillMaxSize())
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(SurfaceDarkContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Camera Off", color = Color.Gray, fontSize = 18.sp)
                    }
                }

                if (state.showOverlay) {
                    TypingOverlay(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // ── Stats Panel (collapsible) ─────────────────────────────────────
            AnimatedVisibility(
                visible = state.showStatsPanel,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDarkContainer),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StatItem("WPM", String.format("%.1f", state.wpm))
                        StatItem("Accuracy", String.format("%.0f%%", state.accuracy))
                        StatItem("Keys", state.totalKeyPresses.toString())
                        StatItem("FPS", String.format("%.1f", state.fps))
                        StatItem("Latency", String.format("%.0fms", state.latencyMs))
                    }
                }
            }

            // ── Last Key + Bottom Bar ─────────────────────────────────────────
            BottomAppBar(
                containerColor = SurfaceDark,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Last pressed key
                    Box(
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDarkContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.lastKeyPressed.ifEmpty { "—" },
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.lastKeyPressed.isNotEmpty()) KeyPress else Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Bluetooth status
                    IconButton(onClick = { viewModel.requestBluetoothPairing() }) {
                        Icon(
                            imageVector = if (state.bluetoothConnected)
                                Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                            contentDescription = "Bluetooth",
                            tint = if (state.bluetoothConnected) KeyPress else Color.Gray,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    // Settings
                    IconButton(onClick = { /* navigate to settings */ }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.Gray,
                        )
                    }

                    // Developer mode toggle
                    IconButton(onClick = { viewModel.toggleDeveloperMode() }) {
                        Icon(
                            Icons.Default.DeveloperMode,
                            contentDescription = "Developer Mode",
                            tint = if (state.developerModeEnabled) KeyPress else Color.Gray,
                        )
                    }

                    // Overlay toggle
                    IconButton(onClick = { viewModel.toggleOverlay() }) {
                        Icon(
                            imageVector = if (state.showOverlay)
                                Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Overlay",
                            tint = Color.Gray,
                        )
                    }

                    // Stats toggle
                    IconButton(onClick = { viewModel.toggleStatsPanel() }) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = "Stats",
                            tint = if (state.showStatsPanel) DeepBlueDark else Color.Gray,
                        )
                    }
                }
            }
        }

        // ── FAB: Toggle camera ────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { viewModel.toggleCamera() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-12).dp, y = (-80).dp)
                .size(48.dp),
            containerColor = SurfaceDarkContainer,
            shape = CircleShape,
        ) {
            Icon(
                imageVector = if (state.cameraActive) Icons.Default.CameraAlt else Icons.Default.CameraOff,
                contentDescription = "Toggle Camera",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ── Camera Preview Composable ─────────────────────────────────────────────────

@Composable
private fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = PreviewView.ScaleType.FIT_CENTER
                id = android.R.id.primary

                val cameraExecutor = Executors.newSingleThreadExecutor()
                val providerFuture = ProcessCameraProvider.getInstance(ctx)

                providerFuture.addListener({
                    val cameraProvider = providerFuture.get()
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()

                    val preview = Preview.Builder().build()
                    preview.surfaceProvider = this.surfaceProvider

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        imageProxy.close()
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            context as androidx.lifecycle.LifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis,
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        update = { },
    )
}

// ── Typing Overlay ────────────────────────────────────────────────────────────

@Composable
private fun TypingOverlay(
    state: TypingUiState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Keyboard outline
        if (state.detectedCorners.isNotEmpty() && state.detectedCorners.size >= 4) {
            val corners = state.detectedCorners
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(corners[0].x * canvasWidth, corners[0].y * canvasHeight)
                lineTo(corners[1].x * canvasWidth, corners[1].y * canvasHeight)
                lineTo(corners[2].x * canvasWidth, corners[2].y * canvasHeight)
                lineTo(corners[3].x * canvasWidth, corners[3].y * canvasHeight)
                close()
            }
            drawPath(path, color = KeyboardHighlight, style = Stroke(width = 3f))
        }

        // Homography grid (developer mode)
        if (state.developerModeEnabled && state.currentHomography != null) {
            val gridSpacing = 0.1f
            var x = 0f
            while (x <= 1f) {
                drawLine(
                    color = HomographyGrid.copy(alpha = 0.3f),
                    start = Offset(x * canvasWidth, 0f),
                    end = Offset(x * canvasWidth, canvasHeight),
                    strokeWidth = 1f,
                )
                x += gridSpacing
            }
            var y = 0f
            while (y <= 1f) {
                drawLine(
                    color = HomographyGrid.copy(alpha = 0.3f),
                    start = Offset(0f, y * canvasHeight),
                    end = Offset(canvasWidth, y * canvasHeight),
                    strokeWidth = 1f,
                )
                y += gridSpacing
            }
        }

        // Fingertips
        for (fingertip in state.detectedFingertips) {
            val fx = fingertip.position.x * canvasWidth
            val fy = fingertip.position.y * canvasHeight

            val isPressed = state.lastKeyPressed.isNotEmpty()

            drawCircle(
                color = if (isPressed) KeyPress else FingerPosition,
                radius = if (isPressed) 18f else 12f,
                center = Offset(fx, fy),
                alpha = fingertip.confidence.coerceIn(0.3f, 1f),
            )

            // Velocity vector (developer mode)
            if (state.developerModeEnabled) {
                val vx = fingertip.velocity.x * 2f
                val vy = fingertip.velocity.y * 2f
                drawLine(
                    color = VelocityVector,
                    start = Offset(fx, fy),
                    end = Offset(fx + vx, fy + vy),
                    strokeWidth = 2f,
                )
            }
        }

        // Developer mode text overlay
        if (state.developerModeEnabled) {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(180, 200, 200, 200)
                textSize = 36f
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                "FPS: ${String.format("%.1f", state.fps)}",
                20f,
                60f,
                paint,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Latency: ${String.format("%.0fms", state.latencyMs)}",
                20f,
                100f,
                paint,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Fingertips: ${state.detectedFingertips.size}",
                20f,
                140f,
                paint,
            )
        }
    }
}

// ── Stat Item ─────────────────────────────────────────────────────────────────

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray,
        )
    }
}
