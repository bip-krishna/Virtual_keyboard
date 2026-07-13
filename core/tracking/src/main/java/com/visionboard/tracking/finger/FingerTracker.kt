package com.visionboard.tracking.finger

import android.graphics.PointF
import com.visionboard.common.models.FingerType
import com.visionboard.tracking.kalman.KalmanFilter
import com.visionboard.tracking.kalman.TemporalSmoother
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

data class TrackedFinger(
    val type: FingerType,
    val position: PointF,
    val velocity: PointF,
    val acceleration: Float,
    val isDown: Boolean,
    val framesTracked: Int,
    val avgConfidence: Float,
) {
    val speed: Float
        get() = sqrt((velocity.x * velocity.x + velocity.y * velocity.y).toDouble()).toFloat()

    val isStationary: Boolean
        get() = speed < STATIONARY_VELOCITY_THRESHOLD && abs(acceleration) < STATIONARY_ACCELERATION_THRESHOLD

    val isMovingDown: Boolean
        get() = velocity.y > DOWNWARD_VELOCITY_THRESHOLD && speed > MOVEMENT_SPEED_THRESHOLD

    companion object {
        const val STATIONARY_VELOCITY_THRESHOLD = 15f
        const val STATIONARY_ACCELERATION_THRESHOLD = 8f
        const val DOWNWARD_VELOCITY_THRESHOLD = 50f
        const val MOVEMENT_SPEED_THRESHOLD = 30f
    }
}

private class FingerState(
    val kalmanFilter: KalmanFilter,
    val smoother: TemporalSmoother,
    var lastPosition: PointF = PointF(0f, 0f),
    var lastVelocity: PointF = PointF(0f, 0f),
    var lastTimestamp: Long = 0L,
    var frameCount: Int = 0,
    var totalConfidence: Float = 0f,
    var stableFrameCount: Int = 0,
    var isDown: Boolean = false,
    var lastDownTimestamp: Long = 0L,
    var lastUpTimestamp: Long = 0L,
    var positionHistory: MutableList<PointF> = mutableListOf(),
    var velocityHistory: MutableList<PointF> = mutableListOf(),
) {
    val trackedFinger: TrackedFinger
        get() = TrackedFinger(
            type = FingerType.THUMB,
            position = PointF(lastPosition.x, lastPosition.y),
            velocity = PointF(lastVelocity.x, lastVelocity.y),
            acceleration = computeAcceleration(),
            isDown = isDown,
            framesTracked = frameCount,
            avgConfidence = if (frameCount > 0) totalConfidence / frameCount else 0f,
        )

    fun computeAcceleration(): Float {
        if (velocityHistory.size < 2) return 0f
        val recent = velocityHistory[velocityHistory.size - 1]
        val previous = velocityHistory[velocityHistory.size - 2]
        val dt = 1f / 30f
        val dvx = (recent.x - previous.x) / dt
        val dvy = (recent.y - previous.y) / dt
        return sqrt(dvx * dvx + dvy * dvy)
    }

    fun addToHistory(position: PointF, velocity: PointF, maxHistory: Int = HISTORY_SIZE) {
        positionHistory.add(PointF(position.x, position.y))
        velocityHistory.add(PointF(velocity.x, velocity.y))
        if (positionHistory.size > maxHistory) {
            positionHistory.removeAt(0)
        }
        if (velocityHistory.size > maxHistory) {
            velocityHistory.removeAt(0)
        }
    }

    fun reset() {
        kalmanFilter.reset()
        smoother.reset()
        lastPosition = PointF(0f, 0f)
        lastVelocity = PointF(0f, 0f)
        lastTimestamp = 0L
        frameCount = 0
        totalConfidence = 0f
        stableFrameCount = 0
        isDown = false
        lastDownTimestamp = 0L
        lastUpTimestamp = 0L
        positionHistory.clear()
        velocityHistory.clear()
    }

    companion object {
        const val HISTORY_SIZE = 15
    }
}

@Singleton
class FingerTracker @Inject constructor() {

    private val fingerStates = mutableMapOf<FingerType, FingerState>()

    private var stableVelocityThreshold = DEFAULT_STABLE_VELOCITY_THRESHOLD
    private var stableFrameThreshold = DEFAULT_STABLE_FRAME_THRESHOLD
    private var downVelocityThreshold = DEFAULT_DOWN_VELOCITY_THRESHOLD
    private var upVelocityThreshold = DEFAULT_UP_VELOCITY_THRESHOLD
    private var downPositionDelta = DEFAULT_DOWN_POSITION_DELTA

    fun updateFinger(
        fingerType: FingerType,
        position: PointF,
        confidence: Float,
    ): TrackedFinger {
        val state = fingerStates.getOrPut(fingerType) {
            FingerState(KalmanFilter(), TemporalSmoother())
        }

        val now = System.currentTimeMillis()
        val dt = if (state.lastTimestamp > 0) {
            ((now - state.lastTimestamp) / 1000f).coerceIn(0.001f, 0.5f)
        } else {
            1f / 30f
        }

        val kalmanPosition = state.kalmanFilter.predict(dt)
        val filteredPosition = state.kalmanFilter.update(position, confidence)

        val smoothedPosition = state.smoother.smooth(filteredPosition, confidence)

        val rawVelocity = PointF(
            (smoothedPosition.x - state.lastPosition.x) / dt,
            (smoothedPosition.y - state.lastPosition.y) / dt,
        )
        val smoothedVelocity = state.smoother.smoothVelocity(rawVelocity)

        state.lastPosition = PointF(smoothedPosition.x, smoothedPosition.y)
        state.lastVelocity = PointF(smoothedVelocity.x, smoothedVelocity.y)
        state.lastTimestamp = now
        state.frameCount++
        state.totalConfidence += confidence

        state.addToHistory(smoothedPosition, smoothedVelocity)

        updateDownState(state, smoothedPosition, smoothedVelocity, now)

        val tracked = TrackedFinger(
            type = fingerType,
            position = PointF(smoothedPosition.x, smoothedPosition.y),
            velocity = PointF(smoothedVelocity.x, smoothedVelocity.y),
            acceleration = state.computeAcceleration(),
            isDown = state.isDown,
            framesTracked = state.frameCount,
            avgConfidence = state.totalConfidence / state.frameCount,
        )

        return tracked
    }

    private fun updateDownState(
        state: FingerState,
        position: PointF,
        velocity: PointF,
        timestamp: Long,
    ) {
        val speed = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
        val isMovingDown = velocity.y > downVelocityThreshold
        val isSlowEnough = speed < upVelocityThreshold || !isMovingDown

        if (!state.isDown) {
            if (isMovingDown && speed > downVelocityThreshold) {
                state.isDown = true
                state.lastDownTimestamp = timestamp
            }
        } else {
            if (state.positionHistory.size >= 2) {
                val prevPos = state.positionHistory[state.positionHistory.size - 2]
                val verticalDelta = position.y - prevPos.y

                if (verticalDelta < -downPositionDelta && speed > upVelocityThreshold) {
                    state.isDown = false
                    state.lastUpTimestamp = timestamp
                } else if (isSlowEnough && speed < STABLE_RELEASE_SPEED_THRESHOLD) {
                    state.stableFrameCount++
                    if (state.stableFrameCount > stableFrameThreshold) {
                        state.isDown = false
                        state.lastUpTimestamp = timestamp
                        state.stableFrameCount = 0
                    }
                } else {
                    state.stableFrameCount = 0
                }
            }
        }
    }

    fun getTrackedFingers(): Map<FingerType, TrackedFinger> {
        return fingerStates.mapValues { (type, state) ->
            TrackedFinger(
                type = type,
                position = PointF(state.lastPosition.x, state.lastPosition.y),
                velocity = PointF(state.lastVelocity.x, state.lastVelocity.y),
                acceleration = state.computeAcceleration(),
                isDown = state.isDown,
                framesTracked = state.frameCount,
                avgConfidence = if (state.frameCount > 0) state.totalConfidence / state.frameCount else 0f,
            )
        }
    }

    fun isFingerStable(fingerType: FingerType): Boolean {
        val state = fingerStates[fingerType] ?: return false
        if (state.frameCount < stableFrameThreshold) return false

        val velocity = state.lastVelocity
        val speed = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
        if (speed > stableVelocityThreshold) {
            state.stableFrameCount = 0
            return false
        }

        val history = state.velocityHistory
        if (history.size < MIN_STABILITY_HISTORY) return false

        val recentWindow = history.takeLast(stableFrameThreshold)
        val avgSpeed = recentWindow.map { v ->
            sqrt(v.x * v.x + v.y * v.y)
        }.average().toFloat()

        return avgSpeed < stableVelocityThreshold
    }

    fun getFingerHistory(fingerType: FingerType): List<PointF> {
        return fingerStates[fingerType]?.positionHistory?.map { PointF(it.x, it.y) } ?: emptyList()
    }

    fun getFingerVelocityHistory(fingerType: FingerType): List<PointF> {
        return fingerStates[fingerType]?.velocityHistory?.map { PointF(it.x, it.y) } ?: emptyList()
    }

    fun setDownVelocityThreshold(threshold: Float) {
        downVelocityThreshold = threshold
    }

    fun setStableThresholds(velocityThreshold: Float, frameThreshold: Int) {
        stableVelocityThreshold = velocityThreshold
        stableFrameThreshold = frameThreshold
    }

    fun reset() {
        fingerStates.values.forEach { it.reset() }
        fingerStates.clear()
    }

    fun resetFinger(fingerType: FingerType) {
        fingerStates[fingerType]?.reset()
        fingerStates.remove(fingerType)
    }

    companion object {
        const val DEFAULT_STABLE_VELOCITY_THRESHOLD = 20f
        const val DEFAULT_STABLE_FRAME_THRESHOLD = 5
        const val DEFAULT_DOWN_VELOCITY_THRESHOLD = 80f
        const val DEFAULT_UP_VELOCITY_THRESHOLD = 30f
        const val DEFAULT_DOWN_POSITION_DELTA = 5f
        const val STABLE_RELEASE_SPEED_THRESHOLD = 15f
        const val MIN_STABILITY_HISTORY = 3
    }
}
