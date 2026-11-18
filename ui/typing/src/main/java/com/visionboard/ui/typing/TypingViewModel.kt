package com.visionboard.ui.typing

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionboard.bluetooth.handler.BluetoothHidHandler
import com.visionboard.bluetooth.handler.ConnectionState
import com.visionboard.common.models.FingerType
import com.visionboard.common.models.FingertipState
import com.visionboard.common.models.FrameResult
import com.visionboard.common.models.HandType as CommonHandType
import com.visionboard.geometry.homography.HomographyEngine
import com.visionboard.settings.SettingsManager
import com.visionboard.tracking.finger.FingerTracker
import com.visionboard.tracking.finger.TrackedFinger
import com.visionboard.tracking.touch.TouchDetector
import com.visionboard.vision.detector.KeyboardDetector
import com.visionboard.vision.hand.HandTracker
import com.visionboard.vision.keyboard.KeyPolygonMapper
import com.visionboard.vision.model.HandLandmarkResult
import com.visionboard.vision.model.HandType as VisionHandType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject

sealed class TypingUiEvent {
    data class KeyPressed(val label: String, val keycode: Int) : TypingUiEvent()
    data object TypingStarted : TypingUiEvent()
    data object TypingStopped : TypingUiEvent()
    data object BluetoothConnected : TypingUiEvent()
    data object BluetoothDisconnected : TypingUiEvent()
    data class ShowToast(val msg: String) : TypingUiEvent()
    data class Error(val msg: String) : TypingUiEvent()
}

@HiltViewModel
class TypingViewModel @Inject constructor(
    private val keyboardDetector: KeyboardDetector,
    private val handTracker: HandTracker,
    private val homographyEngine: HomographyEngine,
    private val keyPolygonMapper: KeyPolygonMapper,
    private val bluetoothHidHandler: BluetoothHidHandler,
    private val settingsManager: SettingsManager,
) : ViewModel() {

    companion object {
        private const val TAG = "TypingViewModel"
        private const val KEY_PRESS_HISTORY_SIZE = 50
        private const val WPM_UPDATE_INTERVAL_MS = 2000L
        private const val DEBOUNCE_INTERVAL_MS = 80L
        private const val FPS_UPDATE_INTERVAL_MS = 1000L
    }

    private val _state = MutableStateFlow(TypingUiState())
    val state: StateFlow<TypingUiState> = _state.asStateFlow()

    private val _typingStats = MutableStateFlow(TypingStats.EMPTY)
    val typingStats: StateFlow<TypingStats> = _typingStats.asStateFlow()

    private val _events = MutableSharedFlow<TypingUiEvent>(extraBufferCapacity = 20)
    val events: SharedFlow<TypingUiEvent> = _events.asSharedFlow()

    private var sessionStartTime = 0L
    private var totalKeyPresses = 0
    private var correctPresses = 0
    private var incorrectPresses = 0
    private var peakWpm = 0f
    private var accumulatedLatency = 0f
    private var latencySamples = 0
    private var accumulativePresses = 0
    private data class HandTracking(val fingers: FingerTracker, val touches: TouchDetector)

    private val handTracking = listOf(CommonHandType.LEFT, CommonHandType.RIGHT)
        .associateWith { HandTracking(FingerTracker(), TouchDetector()) }
    private var cleared = false
    private var manualCalibration = false

    private var lastTouchTime = 0L
    private var lastWpmTime = 0L
    private var lastFpsUpdate = 0L
    private var fpsCounter = 0

    private var pressedKeyHistory = ConcurrentLinkedDeque<String>()

    init {
        loadInitialSettings()
        observeBluetoothConnection()
    }

    private fun observeBluetoothConnection() {
        viewModelScope.launch {
            bluetoothHidHandler.isConnected.collect { isConnected ->
                _state.update {
                    it.copy(
                        bluetoothConnected = isConnected,
                        showConnectionBanner = !isConnected && it.isTypingActive
                    )
                }
            }
        }
        viewModelScope.launch {
            bluetoothHidHandler.connectionState.collect { connState ->
                when (connState) {
                    ConnectionState.CONNECTED -> {
                        _state.update {
                            it.copy(
                                bluetoothConnected = true,
                                showConnectionBanner = false,
                                statusMessage = "Bluetooth connected"
                            )
                        }
                        _events.emit(TypingUiEvent.BluetoothConnected)
                    }
                    ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                        _state.update {
                            it.copy(
                                bluetoothConnected = false,
                                showConnectionBanner = true
                            )
                        }
                        _events.emit(TypingUiEvent.BluetoothDisconnected)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun loadInitialSettings() {
        viewModelScope.launch {
            val settings = settingsManager.observeSettings().first()
            _state.update {
                it.copy(developerModeEnabled = settings.developerMode)
            }
        }
    }

    @Synchronized
    fun startTyping() {
        if (_state.value.isTypingActive) return
        if (_state.value.currentHomography == null) {
            _state.update { it.copy(statusMessage = "Aim the rear camera at the entire keyboard cover") }
            return
        }

        sessionStartTime = System.currentTimeMillis()
        totalKeyPresses = 0
        correctPresses = 0
        incorrectPresses = 0
        peakWpm = 0f
        accumulativePresses = 0
        accumulatedLatency = 0f
        latencySamples = 0
        lastWpmTime = sessionStartTime
        lastTouchTime = 0L
        pressedKeyHistory.clear()
        resetHands()

        _state.update {
            it.copy(
                isTypingActive = true,
                cameraActive = true,
                lastKeyPressed = "",
                totalKeyPresses = 0,
                pressedKeyHistory = emptyList(),
                statusMessage = "Typing active",
            )
        }
        _events.tryEmit(TypingUiEvent.TypingStarted)
    }

    @Synchronized
    fun stopTyping() {
        saveStats()
        resetHands()
        _state.update { it.copy(isTypingActive = false, statusMessage = "Session stopped") }
        _events.tryEmit(TypingUiEvent.TypingStopped)
    }

    fun toggleDeveloperMode() {
        settingsManager.toggleDeveloperMode()
        _state.update { it.copy(developerModeEnabled = !it.developerModeEnabled) }
    }

    fun toggleOverlay() {
        _state.update { it.copy(showOverlay = !it.showOverlay) }
    }

    fun toggleStatsPanel() {
        _state.update { it.copy(showStatsPanel = !it.showStatsPanel) }
    }

    @Synchronized
    fun toggleCamera() {
        val active = !_state.value.cameraActive
        if (!active) {
            stopTyping()
            resetTracking()
            handTracker.close()
        }
        _state.update { it.copy(cameraActive = active) }
    }

    fun onCameraError(message: String) {
        _state.update { it.copy(statusMessage = message) }
    }

    fun dismissConnectionBanner() {
        _state.update { it.copy(showConnectionBanner = false) }
    }

    fun requestBluetoothPairing() {
        if (!bluetoothHidHandler.isConnected()) {
            viewModelScope.launch {
                bluetoothHidHandler.connect().collect {}
            }
        }
    }

    fun sendKeyEvent(keycode: Int, label: String) {
        if (!_state.value.isTypingActive) return

        val now = System.currentTimeMillis()
        if (now - lastTouchTime < DEBOUNCE_INTERVAL_MS) return
        lastTouchTime = now

        bluetoothHidHandler.sendKeyTap(keycode)

        totalKeyPresses++
        accumulativePresses++

        pressedKeyHistory.addFirst(label)
        if (pressedKeyHistory.size > KEY_PRESS_HISTORY_SIZE) {
            pressedKeyHistory.removeLast()
        }

        updateWpm()

        _state.update {
            it.copy(
                lastKeyPressed = label,
                totalKeyPresses = totalKeyPresses,
                pressedKeyHistory = pressedKeyHistory.toList()
            )
        }

        if (label.isNotEmpty()) {
            viewModelScope.launch {
                _events.emit(TypingUiEvent.KeyPressed(label, keycode))
            }
        }
    }

    private fun updateWpm() {
        val now = System.currentTimeMillis()
        val elapsedSec = (now - sessionStartTime) / 1000f
        if (elapsedSec <= 0) return

        val wpm = (accumulativePresses / 5.0f / (elapsedSec / 60.0f)).coerceAtLeast(0f)
        if (wpm > peakWpm) peakWpm = wpm

        _state.update {
            it.copy(wpm = wpm, accuracy = calculateAccuracy())
        }

        if (now - lastWpmTime >= WPM_UPDATE_INTERVAL_MS) {
            lastWpmTime = now
            _typingStats.update { stats ->
                stats.copy(
                    wpm = wpm,
                    totalKeyPresses = totalKeyPresses,
                    correctPresses = correctPresses,
                    incorrectPresses = incorrectPresses,
                    sessionDurationSec = elapsedSec.toLong(),
                    peakWpm = peakWpm
                )
            }
        }
    }

    private fun calculateAccuracy(): Float {
        if (totalKeyPresses == 0) return 100f
        return ((totalKeyPresses - incorrectPresses) * 100f / totalKeyPresses).coerceIn(0f, 100f)
    }

    fun getCurrentStats(): TypingStats {
        val elapsed = if (sessionStartTime > 0) {
            (System.currentTimeMillis() - sessionStartTime) / 1000
        } else 0L
        return TypingStats(
            wpm = _state.value.wpm,
            accuracy = _state.value.accuracy,
            totalKeyPresses = totalKeyPresses,
            correctPresses = correctPresses,
            incorrectPresses = incorrectPresses,
            sessionDurationSec = elapsed,
            peakWpm = peakWpm,
            avgLatencyMs = if (latencySamples > 0) accumulatedLatency / latencySamples else 0f
        )
    }

    private fun saveStats() {
        val stats = getCurrentStats()
        if (stats.totalKeyPresses > 0) {
            _typingStats.value = stats
        }
    }

    fun processFrame(frameResult: FrameResult) {
        if (!_state.value.isTypingActive) return
        if (!frameResult.hasFingertips && !frameResult.keyboardDetected) return

        if (frameResult.fps > 0) {
            _state.update {
                it.copy(
                    fps = frameResult.fps,
                    latencyMs = frameResult.latencyMs,
                    detectedFingertips = frameResult.fingertips,
                    detectedCorners = frameResult.corners ?: emptyList()
                )
            }
            if (frameResult.latencyMs > 0) {
                accumulatedLatency += frameResult.latencyMs
                latencySamples++
            }
        }

        if (frameResult.hasTouches) {
            for (touch in frameResult.touches) {
                if (touch.isHighConfidence) {
                    sendKeyEvent(touch.keycode, touch.keyLabel)
                }
            }
        }
    }

    @Synchronized
    fun processCameraFrame(bitmap: Bitmap) {
        if (cleared || !_state.value.cameraActive) return
        val loopStart = System.nanoTime()
        try {
            val width = bitmap.width
            val height = bitmap.height
            val aspect = width.toFloat() / height
            if (_state.value.frameWidth != width || _state.value.frameHeight != height) {
                resetTracking()
                _state.update { it.copy(frameWidth = width, frameHeight = height) }
            }

            val hands = handTracker.detectHand(bitmap)
            val state = _state.value
            if (!state.isTypingActive && !state.selectingCorners && !manualCalibration) {
                updateKeyboard(bitmap)
            }
            updateHands(hands, width, height)
            updateFpsCounter()

            val latency = (System.nanoTime() - loopStart) / 1_000_000f
            accumulatedLatency += latency
            latencySamples++
            _state.update { it.copy(latencyMs = latency, frameAspectRatio = aspect) }
        } catch (error: Exception) {
            Log.e(TAG, "Camera frame processing failed", error)
            onCameraError("Camera tracking failed. Turn the camera off and on to retry.")
        }
    }

    private fun updateKeyboard(bitmap: Bitmap) {
        val rgba = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val detection = keyboardDetector.detectKeyboard(gray, rgba)
            if (detection.detected && detection.corners.size == 4) {
                val state = _state.value
                val corners = detection.corners.map { PointF(it.x / state.frameWidth, it.y / state.frameHeight) }
                applyKeyboardCorners(corners)
            } else {
                _state.update {
                    it.copy(detectedCorners = emptyList(), currentHomography = null,
                        statusMessage = "Aim the rear camera at the entire keyboard cover")
                }
            }
        } finally {
            gray.release()
            rgba.release()
        }
    }

    @Synchronized
    fun beginManualCalibration() {
        resetTracking()
        _state.update {
            it.copy(selectingCorners = true, statusMessage = "Tap top-left, top-right, bottom-right, bottom-left")
        }
    }

    @Synchronized
    fun addCalibrationCorner(x: Float, y: Float) {
        val state = _state.value
        if (!state.selectingCorners || x !in 0f..1f || y !in 0f..1f) return
        val corners = state.selectedCorners + PointF(x, y)
        if (corners.size < 4) {
            _state.update { it.copy(selectedCorners = corners) }
            return
        }

        // A clockwise convex quadrilateral in camera coordinates has four positive turns.
        val valid = corners.indices.all { index ->
            val a = corners[index]
            val b = corners[(index + 1) % 4]
            val c = corners[(index + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) > 0.001f
        }
        if (valid && applyKeyboardCorners(corners)) {
            manualCalibration = true
            _state.update { it.copy(selectingCorners = false, selectedCorners = emptyList()) }
        } else {
            _state.update {
                it.copy(selectedCorners = emptyList(), statusMessage = "Try again, starting at the top-left corner")
            }
        }
    }

    private fun applyKeyboardCorners(corners: List<PointF>): Boolean {
        val layoutCorners = listOf(PointF(0f, 0f), PointF(1f, 0f), PointF(1f, 1f), PointF(0f, 1f))
        val homography = homographyEngine.computeHomography(corners, layoutCorners)
        try {
            val matrix = homography.matrix?.takeIf { homography.isValid } ?: return false
            val values = DoubleArray(9)
            matrix.get(0, 0, values)
            _state.update {
                it.copy(
                    detectedCorners = corners,
                    currentHomography = FloatArray(9) { index -> values[index].toFloat() },
                    statusMessage = "Keyboard detected. Keep the phone steady.",
                )
            }
            return true
        } finally {
            homography.release()
        }
    }

    private fun updateHands(results: List<HandLandmarkResult>, width: Int, height: Int) {
        val fingertips = mutableListOf<FingertipState>()
        val state = _state.value
        for ((side, tracking) in handTracking) {
            val modelSide = if (side == CommonHandType.LEFT) VisionHandType.LEFT else VisionHandType.RIGHT
            val hand = results.filter { it.handedness == modelSide }.maxByOrNull { it.confidence }
            if (hand == null) {
                tracking.fingers.reset()
                tracking.touches.reset()
                continue
            }

            val keyFingers = mutableMapOf<FingerType, TrackedFinger>()
            for (landmark in hand.landmarks) {
                val pixels = PointF(landmark.x * width, landmark.y * height)
                val tracked = tracking.fingers.updateFinger(landmark.finger, pixels, hand.confidence)
                val imagePosition = PointF(tracked.position.x / width, tracked.position.y / height)
                fingertips.add(
                    FingertipState(
                        position = imagePosition,
                        velocity = tracked.velocity,
                        acceleration = tracked.acceleration,
                        timestamp = System.currentTimeMillis(),
                        confidence = hand.confidence,
                        handType = side,
                        fingerType = landmark.finger,
                    )
                )

                if (state.isTypingActive) {
                    val keyboardPosition = state.currentHomography?.let { project(imagePosition, it) }
                    if (keyboardPosition != null) {
                        keyFingers[landmark.finger] = tracked.copy(position = keyboardPosition)
                    }
                }
            }
            if (state.isTypingActive) {
                tracking.touches.detectTouches(keyFingers, keyPolygonMapper)
                    .forEach { sendKeyEvent(it.keycode, it.keyLabel) }
            }
        }
        _state.update { it.copy(detectedFingertips = fingertips) }
    }

    private fun project(point: PointF, matrix: FloatArray): PointF? {
        val scale = matrix[6] * point.x + matrix[7] * point.y + matrix[8]
        if (!scale.isFinite() || kotlin.math.abs(scale) < 1e-6f) return null
        val x = (matrix[0] * point.x + matrix[1] * point.y + matrix[2]) / scale
        val y = (matrix[3] * point.x + matrix[4] * point.y + matrix[5]) / scale
        return if (x in 0f..1f && y in 0f..1f) PointF(x, y) else null
    }

    private fun updateFpsCounter() {
        val now = System.currentTimeMillis()
        fpsCounter++
        if (now - lastFpsUpdate >= FPS_UPDATE_INTERVAL_MS) {
            val elapsed = (now - lastFpsUpdate) / 1000f
            if (elapsed > 0) {
                val currentFps = fpsCounter / elapsed
                _state.update { it.copy(fps = currentFps) }
            }
            fpsCounter = 0
            lastFpsUpdate = now
        }
    }

    private fun resetHands() {
        for (tracking in handTracking.values) {
            tracking.fingers.reset()
            tracking.touches.reset()
        }
    }

    @Synchronized
    fun resetTracking() {
        resetHands()
        manualCalibration = false
        keyboardDetector.reset()
        fpsCounter = 0
        lastFpsUpdate = System.currentTimeMillis()
        _state.update {
            it.copy(
                isTypingActive = false,
                detectedFingertips = emptyList(),
                detectedCorners = emptyList(),
                selectingCorners = false,
                selectedCorners = emptyList(),
                currentHomography = null,
                fps = 0f,
                latencyMs = 0f,
            )
        }
    }

    @Synchronized
    override fun onCleared() {
        cleared = true
        saveStats()
        bluetoothHidHandler.disconnect()
        handTracker.close()
        resetTracking()
        super.onCleared()
    }
}
