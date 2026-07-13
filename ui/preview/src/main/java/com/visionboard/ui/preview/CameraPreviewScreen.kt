package com.visionboard.ui.preview

import android.util.Size
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

@Composable
fun CameraPreviewScreen(
    modifier: Modifier = Modifier,
    onFrameCaptured: ((ImageProxy) -> Unit)? = null,
    showOverlay: Boolean = false,
    overlayContent: @Composable (Modifier) -> Unit = {},
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val previewState = viewModel.previewState.collectAsState()

    DisposableEffect(context) {
        viewModel.initializeCamera()
        viewModel.setLifecycleOwner(lifecycleOwner)
        onDispose { viewModel.reset() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().clipToBounds(),
            factory = { ctx ->
                FrameLayout(ctx).also { layout ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                    }
                    layout.addView(previewView)

                    val cameraExecutor = Executors.newSingleThreadExecutor()
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)

                    providerFuture.addListener({
                        val cameraProvider = providerFuture.get()

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build()

                        val previewUseCase = Preview.Builder()
                            .build()
                            .apply { surfaceProvider = previewView.surfaceProvider }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val imageCapture = ImageCapture.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { image ->
                            if (onFrameCaptured != null) {
                                onFrameCaptured(image)
                            } else {
                                viewModel.onFrameCaptured(image)
                            }
                            image.close()
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                previewUseCase,
                                imageAnalysis,
                                imageCapture,
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        viewModel.setCameraProvider(cameraProvider)
                        viewModel.setImageCapture(imageCapture)
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            update = { }
        )

        if (showOverlay) {
            overlayContent(Modifier.matchParentSize())
        }
    }
}
