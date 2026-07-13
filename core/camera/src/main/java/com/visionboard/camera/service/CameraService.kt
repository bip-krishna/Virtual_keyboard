package com.visionboard.camera.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import com.visionboard.camera.analyzer.KeyboardFrameAnalyzer
import com.visionboard.camera.config.CameraConfig
import com.visionboard.camera.processor.FrameProcessingResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class CameraService : LifecycleService(), CameraServiceImpl {

    companion object {
        private const val TAG = "CameraService"
        private const val NOTIFICATION_CHANNEL_ID = "visionboard_camera_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.visionboard.camera.ACTION_START"
        const val ACTION_STOP = "com.visionboard.camera.ACTION_STOP"
    }

    @Inject
    lateinit var frameAnalyzer: KeyboardFrameAnalyzer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null

    private val _frames = MutableSharedFlow<FrameProcessingResult>(
        replay = 0,
        extraBufferCapacity = 3
    )
    override val frames: SharedFlow<FrameProcessingResult> = _frames.asSharedFlow()

    private val _cameraState = MutableStateFlow(CameraState.IDLE)
    override val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _currentZoom = MutableStateFlow(1f)
    override val currentZoom: StateFlow<Float> = _currentZoom.asStateFlow()

    private val _isTorchEnabled = MutableStateFlow(false)
    override val isTorchEnabled: StateFlow<Boolean> = _isTorchEnabled.asStateFlow()

    private var currentConfig = CameraConfig.DEFAULT
    private var camera: androidx.camera.core.Camera? = null
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val binder = CameraBinder(this)

    private val lifecycleRegistry = LifecycleRegistry(this).apply {
        currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "CameraService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val config = intent.getSerializableExtra("config") as? CameraConfig
                if (config != null) {
                    currentConfig = config
                }
                startForegroundService()
                initializeCamera()
            }
            ACTION_STOP -> {
                stopPreview()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        releaseCamera()
        analysisExecutor.shutdownNow()
        serviceScope.cancel()
        frameAnalyzer.destroy()
        Log.i(TAG, "CameraService destroyed")
    }

    private fun startForegroundService() {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    private fun initializeCamera() {
        _cameraState.value = CameraState.OPENING
        serviceScope.launch {
            try {
                val providerFuture = ProcessCameraProvider.getInstance(this@CameraService)
                cameraProvider = providerFuture.await()
                cameraProvider?.let { provider ->
                    provider.unbindAll()
                    bindCameraUseCases(provider)
                    _cameraState.value = CameraState.PREVIEWING
                    Log.i(TAG, "Camera initialized and previewing")
                } ?: run {
                    Log.e(TAG, "CameraProvider is null")
                    _cameraState.value = CameraState.ERROR
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize camera", e)
                _cameraState.value = CameraState.ERROR
            }
        }
    }

    private fun bindCameraUseCases(provider: ProcessCameraProvider) {
        val cameraSelector = currentConfig.lensFacing.toCameraSelector()

        preview = Preview.Builder()
            .build()

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetResolution(
                Size(currentConfig.resolution.width, currentConfig.resolution.height)
            )
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(
                Size(currentConfig.resolution.width, currentConfig.resolution.height)
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageAnalysis?.setAnalyzer(analysisExecutor) { imageProxy ->
            frameAnalyzer.analyze(imageProxy)
        }

        serviceScope.launch {
            frameAnalyzer.frames.collect { result ->
                _frames.emit(result)
            }
        }

        camera = provider.bindToLifecycle(
            this@CameraService,
            cameraSelector,
            preview,
            imageCapture,
            imageAnalysis
        )

        camera?.let { cam ->
            _currentZoom.value = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
            _isTorchEnabled.value =
                cam.cameraInfo.torchState.value == androidx.camera.core.CameraState.TORCH_STATE_ON

            cam.cameraInfo.zoomState.observe(this@CameraService) { zoomState ->
                _currentZoom.value = zoomState.zoomRatio
            }
            cam.cameraInfo.torchState.observe(this@CameraService) { torchState ->
                _isTorchEnabled.value =
                    torchState == androidx.camera.core.CameraState.TORCH_STATE_ON
            }

            cam.cameraControl.enableTorch(currentConfig.useHighSpeed)
        }
    }

    private fun releaseCamera() {
        cameraProvider?.unbindAll()
        camera = null
        preview = null
        imageCapture = null
        imageAnalysis = null
    }

    override fun startPreview() {
        if (_cameraState.value == CameraState.PREVIEWING) {
            Log.w(TAG, "Preview already active")
            return
        }
        initializeCamera()
    }

    override fun stopPreview() {
        if (_cameraState.value == CameraState.IDLE) {
            return
        }
        frameAnalyzer.stop()
        releaseCamera()
        _cameraState.value = CameraState.IDLE
        Log.i(TAG, "Preview stopped")
    }

    @OptIn(ExperimentalGetImage::class)
    override fun captureStillImage(onCaptured: (ByteArray) -> Unit) {
        val capture = imageCapture ?: run {
            Log.e(TAG, "ImageCapture not initialized")
            return
        }

        _cameraState.value = CameraState.CAPTURING

        serviceScope.launch(Dispatchers.IO) {
            val outputStream = ByteArrayOutputStream()
            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputStream).build()

            capture.takePicture(
                outputOptions,
                analysisExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val bytes = outputStream.toByteArray()
                        _cameraState.value = CameraState.PREVIEWING
                        serviceScope.launch {
                            onCaptured(bytes)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Still capture failed", exception)
                        _cameraState.value = CameraState.ERROR
                    }
                }
            )
        }
    }

    override fun setZoom(zoomRatio: Float) {
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value ?: return
        val clamped = zoomRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clamped)
        Log.d(TAG, "Zoom set to $clamped")
    }

    override fun zoomIn() {
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value ?: return
        val newRatio = (zoomState.zoomRatio + 0.5f).coerceAtMost(zoomState.maxZoomRatio)
        cam.cameraControl.setZoomRatio(newRatio)
    }

    override fun zoomOut() {
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value ?: return
        val newRatio = (zoomState.zoomRatio - 0.5f).coerceAtLeast(zoomState.minZoomRatio)
        cam.cameraControl.setZoomRatio(newRatio)
    }

    override fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
        Log.d(TAG, "Torch ${if (enabled) "enabled" else "disabled"}")
    }

    override fun toggleTorch() {
        setTorch(!_isTorchEnabled.value)
    }

    override fun destroy() {
        stopPreview()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun setConfig(config: CameraConfig) {
        val wasPreviewing = _cameraState.value == CameraState.PREVIEWING
        if (wasPreviewing) {
            stopPreview()
        }
        currentConfig = config
        if (wasPreviewing) {
            startPreview()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Camera Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "VisionBoard camera capture service"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.let { intent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("VisionBoard")
            .setContentText("Camera is active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
