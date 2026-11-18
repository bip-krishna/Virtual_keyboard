package com.visionboard.ui.typing

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted) cameraPermission.launch(Manifest.permission.CAMERA)
    }

    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode > 0) viewModel.requestBluetoothPairing()
    }
    val showBluetoothDiscovery = {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        discoverable.launch(intent)
    }
    val bluetoothPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) showBluetoothDiscovery()
    }
    val requestBluetooth = {
        if (!state.bluetoothConnected) {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                emptyList()
            }
            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) showBluetoothDiscovery() else bluetoothPermission.launch(missing.toTypedArray())
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(SurfaceDark)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Connection Banner ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.showConnectionBanner && state.isTypingActive,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = requestBluetooth),
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
                if (state.cameraActive && cameraGranted) {
                    CameraPreview(
                        onFrame = viewModel::processCameraFrame,
                        onError = viewModel::onCameraError,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(SurfaceDarkContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cameraGranted) {
                            Text("Camera off", color = Color.Gray, fontSize = 18.sp)
                        } else {
                            TextButton(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) {
                                Text("Allow camera access")
                            }
                        }
                    }
                }

                if ((state.showOverlay || state.selectingCorners) && state.cameraActive && cameraGranted) {
                    TypingOverlay(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        onCornerSelected = viewModel::addCalibrationCorner,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.statusMessage, modifier = Modifier.weight(1f), fontSize = 13.sp)
                if (state.cameraActive && cameraGranted && !state.isTypingActive && state.frameAspectRatio > 0f) {
                    TextButton(onClick = viewModel::beginManualCalibration) {
                        Text(if (state.selectingCorners) "Reset corners" else "Set corners")
                    }
                }
                TextButton(
                    enabled = state.isTypingActive || state.currentHomography != null,
                    onClick = { if (state.isTypingActive) viewModel.stopTyping() else viewModel.startTyping() },
                ) {
                    Text(if (state.isTypingActive) "Stop typing" else "Start typing")
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
                    IconButton(onClick = requestBluetooth) {
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
private fun CameraPreview(
    onFrame: (Bitmap) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier.clipToBounds())

    DisposableEffect(lifecycleOwner, previewView) {
        val cameraExecutor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setOutputImageRotationEnabled(true)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false
        analysis.setAnalyzer(cameraExecutor) { frame ->
            try {
                val bitmap = frame.toBitmap()
                try {
                    onFrame(bitmap)
                } finally {
                    bitmap.recycle()
                }
            } catch (error: Exception) {
                Log.e("TypingCamera", "Could not read camera frame", error)
                onError("Could not read camera frames")
            } finally {
                frame.close()
            }
        }

        providerFuture.addListener({
            if (!disposed) {
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (error: Exception) {
                    Log.e("TypingCamera", "Could not start rear camera", error)
                    onError("Could not start the rear camera")
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            analysis.clearAnalyzer()
            cameraProvider?.unbind(preview, analysis)
            cameraExecutor.shutdown()
        }
    }
}

// ── Typing Overlay ────────────────────────────────────────────────────────────

private fun cameraBounds(size: Size, aspect: Float): Rect {
    val width = minOf(size.width, size.height * aspect)
    val height = width / aspect
    val left = (size.width - width) / 2
    val top = (size.height - height) / 2
    return Rect(left, top, left + width, top + height)
}

@Composable
private fun TypingOverlay(
    state: TypingUiState,
    modifier: Modifier = Modifier,
    onCornerSelected: (Float, Float) -> Unit,
) {
    val aspect = state.frameAspectRatio
    if (aspect <= 0f) return
    val cornerInput = if (state.selectingCorners) {
        Modifier.pointerInput(aspect) {
            detectTapGestures { touch ->
                val bounds = cameraBounds(Size(size.width.toFloat(), size.height.toFloat()), aspect)
                if (bounds.contains(touch)) {
                    onCornerSelected((touch.x - bounds.left) / bounds.width, (touch.y - bounds.top) / bounds.height)
                }
            }
        }
    } else {
        Modifier
    }
    Canvas(modifier = modifier.then(cornerInput)) {
        val bounds = cameraBounds(size, aspect)
        val canvasWidth = bounds.width
        val canvasHeight = bounds.height
        val left = bounds.left
        val top = bounds.top

        for (corner in state.selectedCorners) {
            drawCircle(KeyboardHighlight, radius = 10f,
                center = Offset(left + corner.x * canvasWidth, top + corner.y * canvasHeight))
        }

        // Keyboard outline
        if (state.detectedCorners.isNotEmpty() && state.detectedCorners.size >= 4) {
            val corners = state.detectedCorners
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(left + corners[0].x * canvasWidth, top + corners[0].y * canvasHeight)
                lineTo(left + corners[1].x * canvasWidth, top + corners[1].y * canvasHeight)
                lineTo(left + corners[2].x * canvasWidth, top + corners[2].y * canvasHeight)
                lineTo(left + corners[3].x * canvasWidth, top + corners[3].y * canvasHeight)
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
                    start = Offset(left + x * canvasWidth, top),
                    end = Offset(left + x * canvasWidth, top + canvasHeight),
                    strokeWidth = 1f,
                )
                x += gridSpacing
            }
            var y = 0f
            while (y <= 1f) {
                drawLine(
                    color = HomographyGrid.copy(alpha = 0.3f),
                    start = Offset(left, top + y * canvasHeight),
                    end = Offset(left + canvasWidth, top + y * canvasHeight),
                    strokeWidth = 1f,
                )
                y += gridSpacing
            }
        }

        // Fingertips
        for (fingertip in state.detectedFingertips) {
            val fx = left + fingertip.position.x * canvasWidth
            val fy = top + fingertip.position.y * canvasHeight

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
