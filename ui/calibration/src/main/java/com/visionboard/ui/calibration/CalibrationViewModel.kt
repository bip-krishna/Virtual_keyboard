package com.visionboard.ui.calibration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionboard.common.constants.AppConstants
import com.visionboard.common.models.KeyboardLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

@HiltViewModel
class CalibrationViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<CalibrationUiState>(CalibrationUiState.Idle)
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    private val _currentStep = MutableStateFlow(CalibrationGuideStep.start())
    val currentStep: StateFlow<CalibrationGuideStep> = _currentStep.asStateFlow()

    private val _capturedImages = MutableStateFlow<List<Bitmap>>(emptyList())
    val capturedImages: StateFlow<List<Bitmap>> = _capturedImages.asStateFlow()

    private val _calibrationQuality = MutableStateFlow(0f)
    val calibrationQuality: StateFlow<Float> = _calibrationQuality.asStateFlow()

    private val _showSuccessAnimation = MutableStateFlow(false)
    val showSuccessAnimation: StateFlow<Boolean> = _showSuccessAnimation.asStateFlow()

    private val _showErrorOverlay = MutableStateFlow<String?>(null)
    val showErrorOverlay: StateFlow<String?> = _showErrorOverlay.asStateFlow()

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val captureExecutor = Executors.newSingleThreadExecutor()

    private val maxCaptures = AppConstants.Calibration.IMAGE_COUNT_RANGE.last
    private val minCaptures = AppConstants.Calibration.IMAGE_COUNT_RANGE.first

    fun initializeCamera(context: Context, lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder()
                .setTargetResolution(
                    android.util.Size(
                        AppConstants.Camera.DEFAULT_WIDTH,
                        AppConstants.Camera.DEFAULT_HEIGHT,
                    )
                )
                .build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(
                    android.util.Size(
                        AppConstants.Camera.DEFAULT_WIDTH,
                        AppConstants.Camera.DEFAULT_HEIGHT,
                    )
                )
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                )
                _uiState.value = CalibrationUiState.CameraReady
            } catch (e: Exception) {
                _uiState.value = CalibrationUiState.Error(
                    "Camera initialization failed: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun onCameraPreviewReady(previewView: PreviewView) {
        if (_uiState.value is CalibrationUiState.CameraReady) {
            val step = _currentStep.value
            _uiState.value = CalibrationUiState.Capturing(
                step = step.stepNumber,
                totalSteps = step.totalSteps,
                instruction = step.instruction,
            )
        }
    }

    fun captureImage() {
        val capture = imageCapture ?: return
        val currentState = _uiState.value
        if (currentState !is CalibrationUiState.Capturing) return

        if (_capturedImages.value.size >= maxCaptures) return

        _uiState.value = CalibrationUiState.Processing(
            capturedCount = _capturedImages.value.size,
        )

        capture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                @OptIn(ExperimentalGetImage::class)
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = imageProxyToBitmap(image)
                        val processed = processCapturedImage(bitmap)
                        if (processed != null) {
                            viewModelScope.launch {
                                addCapturedImage(processed)
                                val quality = computeQuality()
                                _calibrationQuality.value = quality
                                _showSuccessAnimation.value = true
                                kotlinx.coroutines.delay(800L)
                                _showSuccessAnimation.value = false
                                advanceOrFinish()
                            }
                        } else {
                            viewModelScope.launch {
                                showError("No keyboard detected. Please reposition and try again.")
                            }
                        }
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    viewModelScope.launch {
                        showError("Capture failed: ${exception.localizedMessage ?: "Unknown error"}")
                    }
                }
            },
        )
    }

    fun retakeLast() {
        val current = _capturedImages.value.toMutableList()
        if (current.isNotEmpty()) {
            current.removeLastOrNull()
            _capturedImages.value = current
            _calibrationQuality.value = computeQuality()

            if (current.isEmpty()) {
                val step = _currentStep.value
                _uiState.value = CalibrationUiState.Capturing(
                    step = step.stepNumber,
                    totalSteps = step.totalSteps,
                    instruction = step.instruction,
                )
            }
        }
    }

    fun completeCalibration() {
        val images = _capturedImages.value
        if (images.size < minCaptures) {
            viewModelScope.launch {
                showError("Need at least $minCaptures captures to complete calibration.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = CalibrationUiState.Processing(capturedCount = images.size)

            val result = withContext(Dispatchers.Default) {
                runCalibrationPipeline(images)
            }

            if (result != null) {
                _uiState.value = CalibrationUiState.Success(
                    layout = result.layout,
                    featureCount = result.featureCount,
                    confidence = result.confidence,
                )
            } else {
                showError("Calibration failed. Please try again with clearer images.")
            }
        }
    }

    fun skipCalibration() {
        _uiState.value = CalibrationUiState.Skipped
    }

    fun dismissError() {
        _showErrorOverlay.value = null
        val step = _currentStep.value
        val captured = _capturedImages.value.size
        _uiState.value = CalibrationUiState.Capturing(
            step = step.stepNumber,
            totalSteps = step.totalSteps,
            instruction = step.instruction,
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val rotation = image.imageInfo.rotationDegrees
        return if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun processCapturedImage(bitmap: Bitmap): Bitmap? {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(blurred, edges, 50.0, 150.0)

        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(
            edges,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE,
        )
        hierarchy.release()

        val keyboardContour = contours
            .filter { contour ->
                val area = Imgproc.contourArea(contour)
                area in AppConstants.FeatureDetection.MIN_CONTOUR_AREA..5_000_000.0
            }
            .maxByOrNull { Imgproc.contourArea(it) }

        val detected = keyboardContour != null

        mat.release()
        gray.release()
        blurred.release()
        edges.release()
        contours.forEach { it.release() }

        return if (detected) bitmap else null
    }

    private suspend fun addCapturedImage(bitmap: Bitmap) {
        _capturedImages.update { current -> current + bitmap }
    }

    private fun advanceOrFinish() {
        val step = _currentStep.value
        val capturedCount = _capturedImages.value.size

        if (step.isLastStep || capturedCount >= maxCaptures) {
            if (capturedCount >= minCaptures) {
                completeCalibration()
            } else {
                val nextStep = step.next()
                _currentStep.value = nextStep
                _uiState.value = CalibrationUiState.Capturing(
                    step = nextStep.stepNumber,
                    totalSteps = nextStep.totalSteps,
                    instruction = nextStep.instruction,
                )
            }
        } else {
            val nextStep = step.next()
            _currentStep.value = nextStep
            _uiState.value = CalibrationUiState.Capturing(
                step = nextStep.stepNumber,
                totalSteps = nextStep.totalSteps,
                instruction = nextStep.instruction,
            )
        }
    }

    private fun computeQuality(): Float {
        val count = _capturedImages.value.size
        if (count == 0) return 0f
        val baseQuality = count.toFloat() / maxCaptures
        return (baseQuality * 0.8f + 0.2f).coerceIn(0f, 1f)
    }

    private suspend fun runCalibrationPipeline(
        images: List<Bitmap>,
    ): CalibrationPipelineResult? = withContext(Dispatchers.Default) {
        var totalFeatures = 0
        val mats = mutableListOf<Mat>()

        try {
            for (bitmap in images) {
                val mat = Mat()
                Utils.bitmapToMat(bitmap, mat)
                val gray = Mat()
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

                val edges = Mat()
                Imgproc.Canny(gray, edges, 50.0, 150.0)

                val hierarchy = Mat()
                val contours = mutableListOf<MatOfPoint>()
                Imgproc.findContours(
                    edges,
                    contours,
                    hierarchy,
                    Imgproc.RETR_EXTERNAL,
                    Imgproc.CHAIN_APPROX_SIMPLE,
                )
                hierarchy.release()

                val validContours = contours.filter {
                    Imgproc.contourArea(it) > AppConstants.FeatureDetection.MIN_CONTOUR_AREA
                }
                totalFeatures += validContours.size

                mats.add(gray)
                mat.release()
                edges.release()
                contours.forEach { it.release() }
            }

            if (totalFeatures < AppConstants.Calibration.MIN_CORNERS_FOR_HOMOGRAPHY) {
                return@withContext null
            }

            val confidence = (totalFeatures.toFloat() / (images.size * 10)).coerceIn(0f, 1f)
            val dummyLayout = KeyboardLayout(
                name = "Calibrated Layout",
                keys = emptyList(),
                bounds = android.graphics.RectF(0f, 0f, 1000f, 500f),
            )

            CalibrationPipelineResult(
                layout = dummyLayout,
                featureCount = totalFeatures,
                confidence = confidence,
            )
        } finally {
            mats.forEach { it.release() }
        }
    }

    private fun showError(message: String) {
        _showErrorOverlay.value = message
    }

    override fun onCleared() {
        super.onCleared()
        captureExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}

sealed class CalibrationUiState {
    data object Idle : CalibrationUiState()
    data object CameraReady : CalibrationUiState()
    data class Capturing(
        val step: Int,
        val totalSteps: Int,
        val instruction: String,
    ) : CalibrationUiState()
    data class Processing(val capturedCount: Int) : CalibrationUiState()
    data class Success(
        val layout: KeyboardLayout,
        val featureCount: Int,
        val confidence: Float,
    ) : CalibrationUiState()
    data class Error(val message: String) : CalibrationUiState()
    data object Skipped : CalibrationUiState()
}

private data class CalibrationPipelineResult(
    val layout: KeyboardLayout,
    val featureCount: Int,
    val confidence: Float,
)
