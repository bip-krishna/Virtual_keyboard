package com.visionboard.ui.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import javax.inject.Inject

data class PreviewUiState(
    val isCameraActive: Boolean = false,
    val zoomLevel: Float = 1f,
    val isTorchEnabled: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel(), DefaultLifecycleObserver {

    private val _previewState = MutableStateFlow(PreviewUiState())
    val previewState: StateFlow<PreviewUiState> = _previewState.asStateFlow()

    private val _lastCapturedImage = MutableSharedFlow<Bitmap>(extraBufferCapacity = 1)
    val lastCapturedImage: SharedFlow<Bitmap> = _lastCapturedImage.asSharedFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var captureModeEnabled = false

    private val captureMutex = Mutex()
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraExecutor.shutdown()
    }

    fun initializeCamera() {
        _previewState.value = _previewState.value.copy(isCameraActive = true)
    }

    fun setCameraProvider(provider: ProcessCameraProvider) {
        cameraProvider = provider
    }

    fun setImageCapture(capture: ImageCapture) {
        imageCapture = capture
    }

    fun setLifecycleOwner(owner: LifecycleOwner) {
        lifecycleOwner = owner
    }

    fun getLifecycleOwner(): LifecycleOwner {
        return lifecycleOwner ?: throw IllegalStateException("LifecycleOwner not set")
    }

    fun enableCaptureMode(enable: Boolean) {
        captureModeEnabled = enable
    }

    fun isCaptureModeEnabled(): Boolean = captureModeEnabled

    fun onFrameCaptured(image: ImageProxy) {
        image.close()
    }

    fun captureFrame(onCaptured: (Bitmap) -> Unit) {
        viewModelScope.launch {
            captureMutex.withLock {
                val capture = imageCapture ?: return@withLock
                try {
                    val dir = File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_PICTURES
                        ),
                        "VisionBoard"
                    )
                    dir.mkdirs()
                    val file = File(dir, "preview_${System.currentTimeMillis()}.jpg")

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                    val semaphore = Semaphore(0)
                    var result: Bitmap? = null

                    capture.takePicture(
                        outputOptions,
                        mainExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                result = BitmapFactory.decodeFile(file.absolutePath)
                                semaphore.release()
                            }

                            override fun onError(exception: ImageCaptureException) {
                                exception.printStackTrace()
                                semaphore.release()
                            }
                        }
                    )
                    semaphore.acquire()
                    result?.let { bmp ->
                        _lastCapturedImage.tryEmit(bmp)
                        withContext(Dispatchers.Main) { onCaptured(bmp) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setZoomLevel(zoom: Float) {
        _previewState.value = _previewState.value.copy(zoomLevel = zoom)
    }

    fun toggleTorch() {
        _previewState.value = _previewState.value.copy(
            isTorchEnabled = !_previewState.value.isTorchEnabled
        )
    }

    fun reset() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
        _previewState.value = PreviewUiState()
    }
}
