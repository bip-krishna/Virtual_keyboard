package com.visionboard.ui.calibration

import android.Manifest
import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.visionboard.ui.theme.DeepBlueLight
import com.visionboard.ui.theme.ErrorRed
import com.visionboard.ui.theme.KeyPress
import com.visionboard.ui.theme.OnSurfaceDark
import com.visionboard.ui.theme.SurfaceDark
import com.visionboard.ui.theme.TealLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CalibrationScreen(
    viewModel: CalibrationViewModel,
    onCalibrationComplete: () -> Unit,
    onCalibrationSkipped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val capturedImages by viewModel.capturedImages.collectAsState()
    val calibrationQuality by viewModel.calibrationQuality.collectAsState()
    val showSuccessAnimation by viewModel.showSuccessAnimation.collectAsState()
    val showErrorOverlay by viewModel.showErrorOverlay.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted },
    )

    LaunchedEffect(Unit) {
        hasCameraPermission = context.checkSelfPermission(
            Manifest.permission.CAMERA,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission && uiState is CalibrationUiState.Idle) {
            viewModel.initializeCamera(context, lifecycleOwner)
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is CalibrationUiState.Success -> onCalibrationComplete()
            is CalibrationUiState.Skipped -> onCalibrationSkipped()
            else -> {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            !hasCameraPermission -> CameraPermissionRationale(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            )

            uiState is CalibrationUiState.Idle || uiState is CalibrationUiState.CameraReady -> {
                LoadingCameraOverlay()
            }

            uiState is CalibrationUiState.Processing -> {
                val count = (uiState as CalibrationUiState.Processing).capturedCount
                ProcessingOverlay(capturedCount = count)
            }

            uiState is CalibrationUiState.Error -> {
                ErrorScreen(
                    message = (uiState as CalibrationUiState.Error).message,
                    onDismiss = { viewModel.dismissError() },
                )
            }

            else -> {
                CalibrationCameraView(
                    currentStep = currentStep,
                    capturedImages = capturedImages,
                    calibrationQuality = calibrationQuality,
                    showSuccessAnimation = showSuccessAnimation,
                    showErrorOverlay = showErrorOverlay,
                    onCapture = { viewModel.captureImage() },
                    onRetake = { viewModel.retakeLast() },
                    onFinish = { viewModel.completeCalibration() },
                    onSkip = { viewModel.skipCalibration() },
                    onDismissError = { viewModel.dismissError() },
                    onCameraReady = { previewView ->
                        viewModel.onCameraPreviewReady(previewView)
                    },
                )
            }
        }
    }
}

@Composable
private fun CalibrationCameraView(
    currentStep: CalibrationGuideStep,
    capturedImages: List<Bitmap>,
    calibrationQuality: Float,
    showSuccessAnimation: Boolean,
    showErrorOverlay: String?,
    onCapture: () -> Unit,
    onRetake: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    onDismissError: () -> Unit,
    onCameraReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val minCaptures = 3
    val maxCaptures = 5
    val canFinish = capturedImages.size >= minCaptures

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                }
            },
            onPreview = { previewView ->
                onCameraReady(previewView)
            },
            modifier = Modifier.fillMaxSize(),
        )

        CameraGuideOverlay()

        StepIndicator(
            step = currentStep.stepNumber,
            totalSteps = currentStep.totalSteps,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
        )

        SkipButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp),
        )

        InstructionOverlay(
            step = currentStep,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        QualityRing(
            progress = calibrationQuality,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 100.dp, start = 16.dp),
        )

        AnimatedVisibility(
            visible = showSuccessAnimation,
            enter = scaleIn(initialScale = 0.3f) + fadeIn(),
            exit = scaleOut(targetScale = 1.5f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            SuccessCheckmark()
        }

        AnimatedVisibility(
            visible = showErrorOverlay != null,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.Center),
        ) {
            showErrorOverlay?.let { message ->
                ErrorOverlay(
                    message = message,
                    onDismiss = onDismissError,
                )
            }
        }

        ThumbnailStrip(
            images = capturedImages,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        CaptureControls(
            canCapture = capturedImages.size < maxCaptures,
            canFinish = canFinish,
            canRetake = capturedImages.isNotEmpty(),
            onCapture = onCapture,
            onRetake = onRetake,
            onFinish = onFinish,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun CameraGuideOverlay(
    modifier: Modifier = Modifier,
) {
    val guideAlpha by animateFloatAsState(
        targetValue = 0.35f,
        animationSpec = tween(durationMillis = 600),
        label = "guideAlpha",
    )

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(color = Color.Black.copy(alpha = guideAlpha * 0.6f))

                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val guideWidth = size.width * 0.82f
                val guideHeight = size.height * 0.45f
                val left = centerX - guideWidth / 2f
                val top = centerY - guideHeight / 2f

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.25f),
                    topLeft = Offset(left, top),
                    size = Size(guideWidth, guideHeight),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.06f),
                    topLeft = Offset(left, top),
                    size = Size(guideWidth, guideHeight),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                )

                val cornerRadius = 4.dp.toPx() * pulseScale
                val cornerAlpha = 0.2f * pulseScale
                drawCircle(
                    color = TealLight.copy(alpha = cornerAlpha),
                    radius = cornerRadius,
                    center = Offset(centerX, top),
                )
                drawCircle(
                    color = TealLight.copy(alpha = cornerAlpha),
                    radius = cornerRadius,
                    center = Offset(centerX, top + guideHeight),
                )
                drawCircle(
                    color = TealLight.copy(alpha = cornerAlpha),
                    radius = cornerRadius,
                    center = Offset(left, centerY),
                )
                drawCircle(
                    color = TealLight.copy(alpha = cornerAlpha),
                    radius = cornerRadius,
                    center = Offset(left + guideWidth, centerY),
                )
            },
    )
}

@Composable
private fun StepIndicator(
    step: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = SurfaceDark.copy(alpha = 0.85f),
        contentColor = OnSurfaceDark,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Step $step of $totalSteps",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                ),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(totalSteps) { index ->
                    val isCompleted = index < step - 1
                    val isCurrent = index == step - 1

                    val dotColor by animateColorAsState(
                        targetValue = when {
                            isCompleted -> TealLight
                            isCurrent -> DeepBlueLight
                            else -> Color.White.copy(alpha = 0.3f)
                        },
                        animationSpec = tween(300),
                        label = "dotColor_$index",
                    )

                    val dotSize by animateFloatAsState(
                        targetValue = if (isCurrent) 10f else 6f,
                        animationSpec = tween(300),
                        label = "dotSize_$index",
                    )

                    Box(
                        modifier = Modifier
                            .size(dotSize.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructionOverlay(
    step: CalibrationGuideStep,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(step) {
        visible = false
        delay(150)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
        modifier = modifier.padding(top = 110.dp),
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark.copy(alpha = 0.9f),
            contentColor = OnSurfaceDark,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = null,
                    tint = TealLight,
                    modifier = Modifier.size(32.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = step.instruction,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = step.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDark.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = step.angleHint,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                    ),
                    color = TealLight.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun QualityRing(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "progress",
    )

    val ringColor by animateColorAsState(
        targetValue = when {
            progress >= 0.8f -> KeyPress
            progress >= 0.5f -> TealLight
            else -> DeepBlueLight
        },
        animationSpec = tween(400),
        label = "ringColor",
    )

    Box(
        modifier = modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            )

            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        Text(
            text = "${(animatedProgress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = OnSurfaceDark,
        )
    }
}

@Composable
private fun SuccessCheckmark(
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        )
    }

    Box(
        modifier = modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(KeyPress.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Captured",
            tint = Color.White,
            modifier = Modifier
                .size(56.dp)
                .scale(scale.value),
        )
    }
}

@Composable
private fun ThumbnailStrip(
    images: List<Bitmap>,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = images.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier.padding(bottom = 160.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceDark.copy(alpha = 0.85f),
            tonalElevation = 4.dp,
        ) {
            LazyRow(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(images, key = { it.hashCode() }) { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured frame",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = 2.dp,
                                color = TealLight.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureControls(
    canCapture: Boolean,
    canFinish: Boolean,
    canRetake: Boolean,
    onCapture: () -> Unit,
    onRetake: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = canRetake && canCapture,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.padding(bottom = 12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = OnSurfaceDark,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Retake last",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Retake",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (canCapture) {
            CaptureButton(onClick = onCapture)
        }

        AnimatedVisibility(
            visible = canFinish,
            enter = scaleIn(initialScale = 0.8f) + fadeIn(),
            exit = scaleOut(targetScale = 0.8f) + fadeOut(),
        ) {
            ExtendedFloatingActionButton(
                onClick = onFinish,
                modifier = Modifier.padding(top = 12.dp),
                containerColor = KeyPress,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                text = {
                    Text(
                        text = "Finish Calibration",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun CaptureButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "capturePulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    Box(
        modifier = modifier.size(88.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            DeepBlueLight.copy(alpha = glowAlpha),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(
                    width = 4.dp,
                    color = DeepBlueLight,
                    shape = CircleShape,
                ),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.White,
                contentColor = DeepBlueLight,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = "Capture",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun SkipButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceDark.copy(alpha = 0.7f),
        contentColor = OnSurfaceDark.copy(alpha = 0.8f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Skip calibration",
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Skip",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ErrorRed.copy(alpha = 0.95f),
        contentColor = Color.White,
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Error",
                modifier = Modifier.size(36.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(
                onClick = onDismiss,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Try Again",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CameraPermissionRationale(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = DeepBlueLight,
                modifier = Modifier.size(72.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Camera Access Required",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = OnSurfaceDark,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "VisionBoard needs camera access to calibrate the keyboard layout. " +
                    "Your images are processed on-device and never leave your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceDark.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepBlueLight,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Grant Camera Access",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun LoadingCameraOverlay(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                color = DeepBlueLight,
                strokeWidth = 3.dp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Initializing camera...",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceDark.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ProcessingOverlay(
    capturedCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceDark.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                color = TealLight,
                strokeWidth = 3.dp,
                modifier = Modifier.size(56.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Processing calibration...",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = OnSurfaceDark,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Analyzing $capturedCount captured image${if (capturedCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDark.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(64.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = OnSurfaceDark,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceDark.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeepBlueLight,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Dismiss",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

