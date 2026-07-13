package com.visionboard.ui.typing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionboard.bluetooth.handler.BluetoothHidHandler
import com.visionboard.bluetooth.handler.ConnectionState
import com.visionboard.common.models.FingerType
import com.visionboard.common.models.FingertipState
import com.visionboard.common.models.FrameResult
import com.visionboard.common.models.HandType as CommonHandType
import com.visionboard.common.utils.PerformanceMonitor
import com.visionboard.geometry.calibration.CalibrationEngine
import com.visionboard.geometry.homography.HomographyEngine
import com.visionboard.settings.SettingsManager
import com.visionboard.tracking.finger.FingerTracker
import com.visionboard.tracking.finger.TrackedFinger
import com.visionboard.tracking.gesture.FrameSnapshot
import com.visionboard.tracking.gesture.GestureDetector
import com.visionboard.tracking.gesture.GestureResult
import com.visionboard.tracking.touch.TouchDetector
import com.visionboard.vision.detector.KeyboardDetector
import com.visionboard.vision.hand.HandTracker
import com.visionboard.vision.keyboard.KeyPolygonMapper
import com.visionboard.vision.model.HandLandmarkResult
import com.visionboard.vision.model.KeyboardDetectionResult
import com.visionboard.vision.model.Landmark
import com.visionboard.vision.model.HandType as VisionHandType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opencv.core.Mat
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
    private val fingerTracker: FingerTracker,
    private val touchDetector: TouchDetector,
    private val gestureDetector: GestureDetector,
    private val homographyEngine: HomographyEngine,
    private val calibrationEngine: CalibrationEngine,
    private val keyPolygonMapper: KeyPolygonMapper,
    private val bluetoothHidHandler: BluetoothHidHandler,
    private val settingsManager: SettingsManager,
    private val performanceMonitor: PerformanceMonitor,
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
    private var homographyValid = false

    private var lastKeyPressTime = 0L
    private var lastTouchTime = 0L
    private var lastWpmTime = 0L
    private var lastFpsUpdate = 0L
    private var fpsCounter = 0

    private var pressedKeysAtFrame = mutableSetOf<String>()
    private var pressedKeyHistory = ConcurrentLinkedDeque<String>()
    private var fingerPositions = mutableMapOf<FingerType, TrackedFinger>()
    private var detectedFingertips = mutableListOf<FingertipState>()

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

    fun startTyping() {
        viewModelScope.launch {
            if (!homographyValid) {
                _state.update { it.copy(statusMessage = "Calibration required before typing") }
                return@launch
            }

            sessionStartTime = System.currentTimeMillis()
            totalKeyPresses = 0
            correctPresses = 0
            incorrectPresses = 0
            peakWpm = 0f
            accumulativePresses = 0
            fpsCounter = 0
            accumulatedLatency = 0f
            latencySamples = 0
            lastFpsUpdate = System.currentTimeMillis()
            lastWpmTime = System.currentTimeMillis()
            fingerPositions.clear()
            pressedKeysAtFrame.clear()
            pressedKeyHistory.clear()
            detectedFingertips.clear()

            performanceMonitor.startFrame()
            touchDetector.reset()
            fingerTracker.reset()
            gestureDetector.reset()

            if (!bluetoothHidHandler.isConnected()) {
                bluetoothHidHandler.connect()
            }

            _state.update {
                it.copy(
                    isTypingActive = true,
                    cameraActive = true,
                    statusMessage = "Typing active"
                )
            }
            _events.emit(TypingUiEvent.TypingStarted)
        }
    }

    fun stopTyping() {
        viewModelScope.launch {
            saveStats()
            bluetoothHidHandler.disconnect()
            performanceMonitor.reset()
            _state.update {
                it.copy(
                    isTypingActive = false,
                    cameraActive = false,
                    statusMessage = "Session stopped"
                )
            }
            _events.emit(TypingUiEvent.TypingStopped)
        }
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

    fun toggleCamera() {
        _state.update { it.copy(cameraActive = !it.cameraActive) }
    }

    fun dismissConnectionBanner() {
        _state.update { it.copy(showConnectionBanner = false) }
    }

    fun requestBluetoothPairing() {
        if (!bluetoothHidHandler.isConnected()) {
            viewModelScope.launch {
                bluetoothHidHandler.connect()
            }
        }
    }

    fun sendKeyEvent(keycode: Int, label: String) {
        if (!_state.value.isTypingActive) return

        val now = System.currentTimeMillis()
        if (now - lastTouchTime < DEBOUNCE_INTERVAL_MS) return
        lastTouchTime = now

        bluetoothHidHandler.sendKeyPress(keycode)
        bluetoothHidHandler.sendKeyRelease(keycode)

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
        val elapsedSec = (now - sessionStartTime) / 1000.0
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

    fun processRawFrame(
        rgbMat: Mat,
        grayMat: Mat,
        timestamp: Long,
    ) {
        if (!_state.value.isTypingActive) return

        updateFpsCounter()
        performanceMonitor.startFrame()

        val loopStart = System.nanoTime()

        try {
            val hasExistingCorners = _state.value.detectedCorners.isNotEmpty()

            if (!hasExistingCorners) {
                val detectionResult = keyboardDetector.detectKeyboard(grayMat, rgbMat)
                if (detectionResult.detected && detectionResult.corners.size == 4) {
                    keyboardDetector.setReferenceFrame(grayMat, detectionResult.corners)
                    onKeyboardDetectionSuccess(detectionResult)
                } else {
                    val orbResult = keyboardDetector.detectUsingORB(grayMat, rgbMat)
                    if (orbResult.detected && orbResult.corners.size == 4) {
                        keyboardDetector.setReferenceFrame(grayMat, orbResult.corners)
                        onKeyboardDetectionSuccess(orbResult)
                    }
                }
            } else {
                val orbResult = keyboardDetector.detectUsingORB(grayMat, rgbMat)
                if (orbResult.detected && orbResult.corners.size >= 4) {
                    onKeyboardDetectionSuccess(orbResult)
                }
            }

            val handLandmarks = handTracker.detectHand(rgbMat)
            if (handLandmarks.isNotEmpty()) {
                processHandLandmarks(handLandmarks, grayMat.cols(), grayMat.rows())
            }

            val trackedFingers = fingerTracker.getTrackedFingers()
            if (trackedFingers.isNotEmpty()) {
                processFingerPositions(trackedFingers)
            }

            if (_state.value.isTypingActive && trackedFingers.isNotEmpty()) {
                val touches = touchDetector.detectTouches(trackedFingers, keyPolygonMapper)
                for (touch in touches) {
                    val now = System.currentTimeMillis()
                    if (touch.isHighConfidence && now - lastTouchTime > DEBOUNCE_INTERVAL_MS) {
                        sendKeyEvent(touch.keycode, touch.keyLabel)
                    }
                }
            }

            if (_state.value.isTypingActive) {
                val frameSnap = FrameSnapshot(
                    fingers = fingerTracker.getTrackedFingers(),
                    timestamp = System.currentTimeMillis(),
                )
                gestureDetector.addFrameSnapshot(frameSnap)

                val trackedFingersForGesture = fingerTracker.getTrackedFingers()
                val gestureResult = gestureDetector.detectGesture(
                    trackedFingersForGesture,
                    emptyList()
                )
                if (gestureResult != null) {
                    processGesture(gestureResult)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in frame processing loop", e)
        } finally {
            val loopEnd = System.nanoTime()
            val latencyMs = (loopEnd - loopStart) / 1_000_000.0f
            accumulatedLatency += latencyMs
            latencySamples++
            performanceMonitor.endFrame()

            if (latencyMs > 0) {
                _state.update { state ->
                    state.copy(
                        fps = calculateFps(),
                        latencyMs = if (latencySamples > 0) accumulatedLatency / latencySamples else 0f
                    )
                }
            }
        }
    }

    private fun onKeyboardDetectionSuccess(result: KeyboardDetectionResult) {
        homographyValid = true
        val corners = result.corners
        _state.update {
            it.copy(
                detectedCorners = corners,
                statusMessage = if (result.detected) "" else "Keyboard tracking lost"
            )
        }

        if (corners.size == 4) {
            val refCorners = listOf(
                PointF(0f, 0f),
                PointF(1f, 0f),
                PointF(1f, 1f),
                PointF(0f, 1f),
            )
            val homography = homographyEngine.computeHomography(corners, refCorners)
            if (homography.isValid && homography.matrix != null) {
                val floatArray = FloatArray(9)
                homography.matrix.get(0, 0, floatArray)
                _state.update { it.copy(currentHomography = floatArray) }
                homography.matrix.release()
            }
        }
    }

    private fun processHandLandmarks(results: List<HandLandmarkResult>, imageWidth: Int, imageHeight: Int) {
        detectedFingertips.clear()

        for (hand in results) {
            if (hand.confidence < 0.5f) continue

            val handType: CommonHandType = when (hand.handedness) {
                VisionHandType.LEFT -> CommonHandType.LEFT
                VisionHandType.RIGHT -> CommonHandType.RIGHT
                VisionHandType.UNKNOWN -> continue
            }

            for (landmark in hand.landmarks) {
                if (landmark.visibility < 0.5f) continue

                val position = handTracker.normalizedToPixel(landmark, imageWidth, imageHeight)
                val fingerType = mapFingertipToFingerType(landmark, hand.landmarks)

                val tracked = fingerTracker.updateFinger(fingerType, position, landmark.visibility)
                fingerPositions[fingerType] = tracked

                detectedFingertips.add(
                    FingertipState(
                        position = position,
                        velocity = PointF(tracked.velocity.x, tracked.velocity.y),
                        acceleration = tracked.acceleration,
                        timestamp = System.currentTimeMillis(),
                        confidence = landmark.visibility,
                        handType = handType,
                        fingerType = fingerType,
                    )
                )

                if (tracked.isDown) {
                    val key = keyPolygonMapper.getKeyPressed(position)
                    if (key != null) {
                        pressedKeysAtFrame.add(key.label)
                    }
                }
            }
        }

        _state.update { it.copy(detectedFingertips = detectedFingertips.toList()) }
    }

    private fun processFingerPositions(fingers: Map<FingerType, TrackedFinger>) {
        val touches = touchDetector.detectTouches(fingers, keyPolygonMapper)
        for (touch in touches) {
            val now = System.currentTimeMillis()
            if (touch.isHighConfidence && now - lastTouchTime > DEBOUNCE_INTERVAL_MS) {
                sendKeyEvent(touch.keycode, touch.keyLabel)
            }
        }
    }

    private fun mapFingertipToFingerType(landmark: Landmark, allLandmarks: List<Landmark>): FingerType {
        return FingerType.INDEX
    }

    private fun processGesture(result: GestureResult) {
        when (result.gesture) {
            com.visionboard.common.models.GestureType.PINCH -> toggleDeveloperMode()
            else -> {}
        }
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

    private fun calculateFps(): Float {
        val metrics = performanceMonitor.getMetrics()
        return metrics.fps
    }

    fun resetTracking() {
        fingerTracker.reset()
        touchDetector.reset()
        gestureDetector.reset()
        fingerPositions.clear()
        pressedKeysAtFrame.clear()
        detectedFingertips.clear()
        homographyValid = false
        lastTouchTime = 0L

        _state.update {
            it.copy(
                detectedFingertips = emptyList(),
                lastKeyPressed = "",
                detectedCorners = emptyList(),
                currentHomography = null,
                statusMessage = "Tracking reset",
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveStats()
        bluetoothHidHandler.disconnect()
        keyboardDetector.reset()
        fingerTracker.reset()
        touchDetector.reset()
        gestureDetector.reset()
        performanceMonitor.reset()
    }
}
