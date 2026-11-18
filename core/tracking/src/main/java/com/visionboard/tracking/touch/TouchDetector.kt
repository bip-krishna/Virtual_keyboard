package com.visionboard.tracking.touch

import android.graphics.PointF
import com.visionboard.common.models.FingerType
import com.visionboard.common.models.GestureType
import com.visionboard.common.models.TouchEvent
import com.visionboard.tracking.finger.TrackedFinger
import com.visionboard.vision.keyboard.KeyPolygonMapper
import com.visionboard.vision.model.KeyData
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

data class FingerTouchState(
    val fingerType: FingerType,
    val pressedKey: KeyData?,
    val contactProbability: Float,
    val dwellTimeMs: Long,
    val isPressed: Boolean,
    val wasPressed: Boolean,
    val framesInProximity: Int,
    val lastContactTimeMs: Long,
    val contactKeyLabel: String?,
)

@Singleton
class TouchDetector @Inject constructor() {

    private var velocityThreshold = DEFAULT_VELOCITY_THRESHOLD
    private var accelerationThreshold = DEFAULT_ACC_THRESHOLD
    private var dwellTimeMs = DEFAULT_DWELL_TIME_MS
    private var debounceMs = DEFAULT_DEBOUNCE_MS
    private var hysteresisOffset = DEFAULT_HYSTERESIS_OFFSET

    private val fingerStates = mutableMapOf<FingerType, InternalTouchState>()
    private val debounceMap = mutableMapOf<String, Long>()
    private var lastTouchEventTime = 0L

    private class InternalTouchState {
        var isPressed = false
        var pressedKeyLabel: String? = null
        var dwellStartMs = 0L
        var dwellAccumulatedMs = 0L
        var framesInProximity = 0
        var totalFrames = 0
        var lastContactTimeMs = 0L
        var motionHistory = mutableListOf<PointF>()
        var velocityHistory = mutableListOf<PointF>()
        var contactProbabilityHistory = mutableListOf<Float>()
        var wasPressed = false
        var pressStartTimeMs = 0L
        var lastPosition = PointF(0f, 0f)
    }

    fun detectTouches(
        fingers: Map<FingerType, TrackedFinger>,
        keyMapper: KeyPolygonMapper,
    ): List<TouchEvent> {
        val now = System.currentTimeMillis()
        val events = mutableListOf<TouchEvent>()

        for ((fingerType, finger) in fingers) {
            if (finger.framesTracked < MIN_FRAMES_BEFORE_TOUCH) continue
            if (finger.avgConfidence < MIN_CONFIDENCE) continue

            val state = fingerStates.getOrPut(fingerType) { InternalTouchState() }
            state.totalFrames++
            state.lastPosition = finger.position

            addToHistory(state, finger)

            val probability = getContactProbability(finger, keyMapper)

            val nearestKey = keyMapper.getKeyPressed(finger.position)
            val isInsideKey = nearestKey != null

            val isPressDetected = evaluatePress(finger, state, probability, isInsideKey)
            val isReleaseDetected = evaluateRelease(finger, state, probability)

            if (!state.isPressed && isPressDetected) {
                state.isPressed = true
                state.wasPressed = true
                state.dwellStartMs = now
                state.dwellAccumulatedMs = 0L
                state.framesInProximity = 0
                state.pressStartTimeMs = now

                val contactKey = nearestKey
                state.pressedKeyLabel = contactKey?.label
                state.lastContactTimeMs = now

                contactKey?.let { key ->
                    val keyCenter = computePolygonCenter(key.relativePolygon)
                    val dist = sqrt(
                        (finger.position.x - keyCenter.x) * (finger.position.x - keyCenter.x) +
                        (finger.position.y - keyCenter.y) * (finger.position.y - keyCenter.y)
                    )
                    val spatialConfidence = (1f - (dist / KEY_PROXIMITY_RADIUS)).coerceIn(0f, 1f)
                    val finalConfidence = (probability * 0.7f + spatialConfidence * 0.3f) *
                        finger.avgConfidence

                    val event = TouchEvent(
                        keyLabel = key.label,
                        keycode = key.keyCode,
                        timestamp = now,
                        confidence = finalConfidence.coerceIn(0f, 1f),
                        gestureType = GestureType.TAP,
                    )
                    events.add(event)
                    lastTouchEventTime = now
                }
            } else if (state.isPressed && isReleaseDetected) {
                state.isPressed = false
                state.dwellAccumulatedMs += now - state.dwellStartMs
                state.pressedKeyLabel = null
            }

            if (state.isPressed) {
                state.framesInProximity++
                state.dwellAccumulatedMs = now - state.dwellStartMs

                val reassignedKey = nearestKey
                if (reassignedKey != null && reassignedKey.label != state.pressedKeyLabel) {
                    state.pressedKeyLabel = reassignedKey.label
                    state.lastContactTimeMs = now
                }
            }
        }

        pruneStaleStates(fingers)
        pruneDebounceMap(now)

        return events
    }

    private fun evaluatePress(
        finger: TrackedFinger,
        state: InternalTouchState,
        contactProbability: Float,
        isInsideKey: Boolean,
    ): Boolean {
        val now = System.currentTimeMillis()

        if (now - lastTouchEventTime < debounceMs) return false
        if (state.isPressed || !isInsideKey) return false

        val pressThreshold = if (state.wasPressed) {
            PRESS_PROBABILITY_THRESHOLD + hysteresisOffset
        } else {
            PRESS_PROBABILITY_THRESHOLD
        }

        val dwellBonus = if (state.framesInProximity >= DWELL_FRAMES_FOR_BONUS) {
            DWELL_BONUS
        } else {
            0f
        }

        val effectiveThreshold = pressThreshold - dwellBonus

        val velocityScore = computeVelocityScore(finger)
        val accelerationScore = computeAccelerationScore(finger)
        val positionScore = if (isInsideKey) POSITION_BONUS else 0f
        val historyScore = computeMotionHistoryScore(state)
        val dwellScore = computeDwellScore(state)

        val weightedScore = contactProbability * PROBABILITY_WEIGHT +
            velocityScore * VELOCITY_WEIGHT +
            accelerationScore * ACCELERATION_WEIGHT +
            positionScore * POSITION_WEIGHT +
            historyScore * HISTORY_WEIGHT +
            dwellScore * DWELL_WEIGHT

        return weightedScore >= effectiveThreshold
    }

    private fun evaluateRelease(
        finger: TrackedFinger,
        state: InternalTouchState,
        contactProbability: Float,
    ): Boolean {
        if (!state.isPressed) return false

        val releaseThreshold = PRESS_PROBABILITY_THRESHOLD - RELEASE_OFFSET

        val speed = finger.speed
        val isMovingUp = finger.velocity.y < -UPWARD_VELOCITY_THRESHOLD
        val isMovingFast = speed > FAST_MOVEMENT_THRESHOLD
        val lowProbability = contactProbability < releaseThreshold
        val dwellExceeded = state.dwellAccumulatedMs > MAX_DWELL_TIME_MS

        return when {
            dwellExceeded -> true
            isMovingUp && isMovingFast -> true
            lowProbability && isMovingFast -> true
            lowProbability && state.framesInProximity > MIN_RELEASE_FRAMES -> true
            else -> false
        }
    }

    fun getContactProbability(finger: TrackedFinger, keyMapper: KeyPolygonMapper): Float {
        val velocityProb = computeVelocityProbability(finger)
        val accelerationProb = computeAccelerationProbability(finger)
        val proximityProb = computeProximityProbability(finger, keyMapper)
        val stabilityProb = computeStabilityProbability(finger)
        val dwellProb = computeDwellProbability(finger)

        val weighted = velocityProb * VELOCITY_PROB_WEIGHT +
            accelerationProb * ACCEL_PROB_WEIGHT +
            proximityProb * PROXIMITY_PROB_WEIGHT +
            stabilityProb * STABILITY_PROB_WEIGHT +
            dwellProb * DWELL_PROB_WEIGHT

        return weighted.coerceIn(0f, 1f)
    }

    private fun computeVelocityScore(finger: TrackedFinger): Float {
        val speed = finger.speed
        val downwardComponent = finger.velocity.y.coerceAtLeast(0f)

        return when {
            speed < velocityThreshold * 0.3f -> 0.1f
            downwardComponent > velocityThreshold * 2f -> 1.0f
            downwardComponent > velocityThreshold -> 0.8f
            downwardComponent > velocityThreshold * 0.5f -> 0.5f
            speed > velocityThreshold -> 0.4f
            else -> 0.2f
        }
    }

    private fun computeAccelerationScore(finger: TrackedFinger): Float {
        val downwardAccel = finger.acceleration
        return when {
            downwardAccel > accelerationThreshold * 2f -> 1.0f
            downwardAccel > accelerationThreshold -> 0.8f
            downwardAccel > accelerationThreshold * 0.5f -> 0.5f
            downwardAccel > 0f -> 0.3f
            else -> 0.1f
        }
    }

    private fun computeMotionHistoryScore(state: InternalTouchState): Float {
        val history = state.velocityHistory
        if (history.size < 3) return 0.5f

        val recentCount = minOf(5, history.size)
        val recentVelocities = history.takeLast(recentCount)

        val consistentDownward = recentVelocities.count { it.y > velocityThreshold * 0.5f }
        val consistencyRatio = consistentDownward.toFloat() / recentCount

        val positionHistory = state.motionHistory
        if (positionHistory.size < 3) return consistencyRatio

        val recentPositions = positionHistory.takeLast(minOf(5, positionHistory.size))
        val yVariance = computeVariance(recentPositions.map { it.y })

        val convergenceScore = if (yVariance < POSITION_VARIANCE_THRESHOLD) 0.8f else 0.3f

        return (consistencyRatio * 0.6f + convergenceScore * 0.4f).coerceIn(0f, 1f)
    }

    private fun computeDwellScore(state: InternalTouchState): Float {
        val dwellMs = state.dwellAccumulatedMs
        return when {
            dwellMs > 500 -> 0.9f
            dwellMs > 300 -> 0.7f
            dwellMs > dwellTimeMs -> 0.5f
            dwellMs > 100 -> 0.3f
            else -> 0.1f
        }
    }

    private fun computeVelocityProbability(finger: TrackedFinger): Float {
        val speed = finger.speed
        val downward = finger.velocity.y.coerceAtLeast(0f)

        return when {
            downward > velocityThreshold * 3f -> 0.95f
            downward > velocityThreshold * 2f -> 0.85f
            downward > velocityThreshold -> 0.7f
            downward > velocityThreshold * 0.5f -> 0.5f
            speed > velocityThreshold -> 0.4f
            speed < 10f -> 0.2f
            else -> 0.3f
        }
    }

    private fun computeAccelerationProbability(finger: TrackedFinger): Float {
        val accel = finger.acceleration
        return when {
            accel > accelerationThreshold * 3f -> 0.9f
            accel > accelerationThreshold * 2f -> 0.75f
            accel > accelerationThreshold -> 0.6f
            accel > accelerationThreshold * 0.5f -> 0.4f
            else -> 0.2f
        }
    }

    private fun computeProximityProbability(finger: TrackedFinger, keyMapper: KeyPolygonMapper): Float {
        val nearestKey = keyMapper.findNearestKey(finger.position) ?: return 0.1f
        val keyCenter = computePolygonCenter(nearestKey.relativePolygon)
        val dist = sqrt(
            (finger.position.x - keyCenter.x) * (finger.position.x - keyCenter.x) +
            (finger.position.y - keyCenter.y) * (finger.position.y - keyCenter.y)
        )

        val isInside = keyMapper.isPointInPolygon(finger.position, nearestKey.relativePolygon)
        if (isInside) return 0.9f

        return when {
            dist < 10f -> 0.8f
            dist < KEY_PROXIMITY_RADIUS * 0.5f -> 0.6f
            dist < KEY_PROXIMITY_RADIUS -> 0.4f
            dist < KEY_PROXIMITY_RADIUS * 2f -> 0.2f
            else -> 0.1f
        }
    }

    private fun computeStabilityProbability(finger: TrackedFinger): Float {
        val speed = finger.speed
        return when {
            speed < 5f -> 0.9f
            speed < 20f -> 0.7f
            speed < 50f -> 0.5f
            speed < 100f -> 0.3f
            else -> 0.1f
        }
    }

    private fun computeDwellProbability(finger: TrackedFinger): Float {
        val state = fingerStates[finger.type] ?: return 0.3f
        val dwellMs = state.dwellAccumulatedMs

        return when {
            dwellMs > 1000 -> 0.95f
            dwellMs > 500 -> 0.8f
            dwellMs > dwellTimeMs -> 0.6f
            dwellMs > 100 -> 0.4f
            else -> 0.2f
        }
    }

    private fun addToHistory(state: InternalTouchState, finger: TrackedFinger) {
        state.motionHistory.add(PointF(finger.position.x, finger.position.y))
        state.velocityHistory.add(PointF(finger.velocity.x, finger.velocity.y))

        if (state.motionHistory.size > MOTION_HISTORY_SIZE) {
            state.motionHistory.removeAt(0)
        }
        if (state.velocityHistory.size > MOTION_HISTORY_SIZE) {
            state.velocityHistory.removeAt(0)
        }
    }

    private fun computePolygonCenter(polygon: List<PointF>): PointF {
        if (polygon.isEmpty()) return PointF(0f, 0f)
        val sumX = polygon.sumOf { it.x.toDouble() }.toFloat()
        val sumY = polygon.sumOf { it.y.toDouble() }.toFloat()
        return PointF(sumX / polygon.size, sumY / polygon.size)
    }

    private fun computeVariance(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        val sumSquaredDiff = values.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat()
        return sumSquaredDiff / values.size
    }

    private fun pruneStaleStates(activeFingers: Map<FingerType, TrackedFinger>) {
        val staleTypes = fingerStates.keys.filter { it !in activeFingers }
        staleTypes.forEach { fingerStates.remove(it) }
    }

    private fun pruneDebounceMap(now: Long) {
        debounceMap.entries.removeIf { now - it.value > debounceMs * 10 }
    }

    fun isPressed(finger: TrackedFinger): Boolean {
        return fingerStates[finger.type]?.isPressed ?: false
    }

    fun isReleased(finger: TrackedFinger): Boolean {
        val state = fingerStates[finger.type] ?: return true
        return !state.isPressed && state.wasPressed
    }

    fun reset() {
        fingerStates.clear()
        debounceMap.clear()
        lastTouchEventTime = 0L
    }

    fun setThresholds(
        velocityThreshold: Float = this.velocityThreshold,
        accelerationThreshold: Float = this.accelerationThreshold,
        dwellTimeMs: Long = this.dwellTimeMs,
        debounceMs: Long = this.debounceMs,
        hysteresisOffset: Float = this.hysteresisOffset,
    ) {
        this.velocityThreshold = velocityThreshold
        this.accelerationThreshold = accelerationThreshold
        this.dwellTimeMs = dwellTimeMs
        this.debounceMs = debounceMs
        this.hysteresisOffset = hysteresisOffset
    }

    companion object {
        const val DEFAULT_VELOCITY_THRESHOLD = 150f
        const val DEFAULT_ACC_THRESHOLD = 200f
        const val DEFAULT_DWELL_TIME_MS = 200L
        const val DEFAULT_DEBOUNCE_MS = 80L
        const val DEFAULT_HYSTERESIS_OFFSET = 0.1f

        const val PRESS_PROBABILITY_THRESHOLD = 0.55f
        const val RELEASE_OFFSET = 0.15f

        const val VELOCITY_PROB_WEIGHT = 0.25f
        const val ACCEL_PROB_WEIGHT = 0.15f
        const val PROXIMITY_PROB_WEIGHT = 0.30f
        const val STABILITY_PROB_WEIGHT = 0.15f
        const val DWELL_PROB_WEIGHT = 0.15f

        const val PROBABILITY_WEIGHT = 0.35f
        const val VELOCITY_WEIGHT = 0.25f
        const val ACCELERATION_WEIGHT = 0.15f
        const val POSITION_WEIGHT = 0.15f
        const val HISTORY_WEIGHT = 0.05f
        const val DWELL_WEIGHT = 0.05f

        const val DWELL_FRAMES_FOR_BONUS = 3
        const val DWELL_BONUS = 0.1f

        const val MIN_FRAMES_BEFORE_TOUCH = 3
        const val MIN_CONFIDENCE = 0.3f
        const val KEY_PROXIMITY_RADIUS = 50f

        const val UPWARD_VELOCITY_THRESHOLD = 100f
        const val FAST_MOVEMENT_THRESHOLD = 200f
        const val MIN_RELEASE_FRAMES = 3
        const val MAX_DWELL_TIME_MS = 2000L

        const val POSITION_VARIANCE_THRESHOLD = 200f
        const val MOTION_HISTORY_SIZE = 15
    }
}
