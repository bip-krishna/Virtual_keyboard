package com.visionboard.camera.analyzer

import android.media.Image
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.visionboard.camera.processor.FrameProcessingResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyboardFrameAnalyzer @Inject constructor() : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "KeyboardFrameAnalyzer"
        private const val DEFAULT_FRAME_SKIP_INTERVAL = 2L
        private const val FPS_MEASUREMENT_WINDOW_MS = 1000L
        private const val CHANNEL_CAPACITY = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _frames = MutableSharedFlow<FrameProcessingResult>(
        replay = 0,
        extraBufferCapacity = CHANNEL_CAPACITY
    )
    val frames: SharedFlow<FrameProcessingResult> = _frames.asSharedFlow()

    private val _currentFps = MutableStateFlow(0f)
    val currentFps: StateFlow<Float> = _currentFps.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val frameChannel = Channel<ImageProxy>(
        capacity = Channel.BUFFERED
    )

    private val frameSkipInterval = AtomicLong(DEFAULT_FRAME_SKIP_INTERVAL)
    private val frameCounter = AtomicLong(0L)
    private val totalFramesProcessed = AtomicLong(0L)
    private val isAnalyzerActive = AtomicBoolean(false)

    private val fpsTimestamps = mutableListOf<Long>()
    private val fpsLock = Mutex()

    private val nv21Buffer: ThreadLocal<ByteArray> = ThreadLocal.withInitial { ByteArray(0) }

    init {
        startFrameProcessingLoop()
    }

    private fun startFrameProcessingLoop() {
        scope.launch {
            for (imageProxy in frameChannel) {
                try {
                    processImageProxy(imageProxy)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing frame", e)
                } finally {
                    imageProxy.close()
                }
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isAnalyzerActive.get()) {
            imageProxy.close()
            return
        }

        val currentFrame = frameCounter.incrementAndGet()
        if (currentFrame % frameSkipInterval.get() != 0L) {
            imageProxy.close()
            return
        }

        _isProcessing.value = true

        val sent = frameChannel.trySend(imageProxy)
        if (sent.isFailure) {
            Log.w(TAG, "Frame channel full, dropping frame #$currentFrame")
            imageProxy.close()
        }
    }

    private suspend fun processImageProxy(imageProxy: ImageProxy) {
        val image = imageProxy.image ?: run {
            Log.w(TAG, "ImageProxy has null image, skipping")
            return
        }

        val timestamp = imageProxy.imageInfo.timestamp
        val frameNumber = frameCounter.get()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        updateFpsTracking()

        try {
            val rgbMat = yuv420ToRgbMat(image, rotationDegrees)
            val grayMat = Mat()
            Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY)

            val result = FrameProcessingResult(
                rgbMat = rgbMat,
                grayMat = grayMat,
                timestamp = timestamp,
                frameNumber = frameNumber
            )

            totalFramesProcessed.incrementAndGet()
            _frames.emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert YUV frame", e)
        } finally {
            _isProcessing.value = false
        }
    }

    private fun yuv420ToRgbMat(image: Image, rotationDegrees: Int): Mat {
        val width = image.width
        val height = image.height
        val planes = image.planes

        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = nv21Buffer.get()
        val requiredSize = ySize + uSize + vSize
        if (nv21.size < requiredSize) {
            nv21Buffer.set(ByteArray(requiredSize))
        }
        val buffer = nv21Buffer.get()

        var pos = 0

        val yRowStride = planes[0].rowStride
        val yPixelStride = planes[0].pixelStride

        for (row in 0 until height) {
            for (col in 0 until width) {
                val yIndex = row * yRowStride + col * yPixelStride
                if (yIndex < ySize) {
                    buffer[pos++] = yBuffer.get(yIndex)
                }
            }
        }

        val uvRowStride = planes[1].rowStride
        val uvPixelStride = planes[1].pixelStride
        val uvHeight = height / 2
        val uvWidth = width / 2

        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val uvIndex = row * uvRowStride + col * uvPixelStride
                if (uvIndex < vSize) {
                    buffer[pos++] = vBuffer.get(uvIndex)
                }
                if (uvIndex < uSize) {
                    buffer[pos++] = uBuffer.get(uvIndex)
                }
            }
        }

        val nv21Mat = Mat(height + uvHeight, width, CvType.CV_8UC1)
        nv21Mat.put(0, 0, buffer, 0, pos)

        val bgrMat = Mat()
        Imgproc.cvtColor(nv21Mat, bgrMat, Imgproc.COLOR_YUV2BGR_NV21)
        nv21Mat.release()

        val rgbMat = Mat()
        Imgproc.cvtColor(bgrMat, rgbMat, Imgproc.COLOR_BGR2RGB)
        bgrMat.release()

        if (rotationDegrees != 0) {
            val rotated = rotateMat(rgbMat, rotationDegrees)
            rgbMat.release()
            return rotated
        }

        return rgbMat
    }

    private fun rotateMat(mat: Mat, degrees: Int): Mat {
        val rotated = Mat()
        val flipCode = when (degrees) {
            90 -> Core.ROTATE_90_CLOCKWISE
            180 -> Core.ROTATE_180
            270 -> Core.ROTATE_90_COUNTERCLOCKWISE
            else -> return mat
        }
        Core.rotate(mat, rotated, flipCode)
        return rotated
    }

    private suspend fun updateFpsTracking() {
        fpsLock.withLock {
            val now = System.currentTimeMillis()
            fpsTimestamps.add(now)

            fpsTimestamps.removeAll { it < now - FPS_MEASUREMENT_WINDOW_MS }

            if (fpsTimestamps.size >= 2) {
                val windowDuration = fpsTimestamps.last() - fpsTimestamps.first()
                if (windowDuration > 0) {
                    val fps = (fpsTimestamps.size - 1) * 1000f / windowDuration
                    _currentFps.value = fps
                }
            }
        }
    }

    fun setFrameSkipInterval(interval: Long) {
        require(interval >= 1) { "Frame skip interval must be >= 1, got $interval" }
        frameSkipInterval.set(interval)
    }

    fun start() {
        isAnalyzerActive.set(true)
        frameCounter.set(0)
        fpsTimestamps.clear()
        Log.i(TAG, "Frame analyzer started")
    }

    fun stop() {
        isAnalyzerActive.set(false)
        frameChannel.close()
        _isProcessing.value = false
        Log.i(TAG, "Frame analyzer stopped. Total frames processed: ${totalFramesProcessed.get()}")
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
}
