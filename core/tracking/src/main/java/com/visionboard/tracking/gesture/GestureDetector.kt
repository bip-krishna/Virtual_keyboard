package com.visionboard.tracking.gesture

import android.graphics.PointF
import com.visionboard.common.models.FingerType
import com.visionboard.common.models.GestureType
import com.visionboard.tracking.finger.TrackedFinger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

data class FrameSnapshot(
    val fingers: Map<FingerType, TrackedFinger>,
    val timestamp: Long,
)

data class GestureResult(
    val gesture: GestureType,
    val confidence: Float,
    val position: PointF,
)

@Singleton
class GestureDetector @Inject constructor() {

    private val frameHistory = ArrayDeque<FrameSnapshot>(MAX_FRAME_HISTORY + 1)
    private var gestureState = InternalGestureState()

    private class InternalGestureState {
        var thumbTapStartY = 0f
        var thumbTapStartTimeMs = 0L
        var thumbTapActive = false

        var pinchStartDistance = 0f
        var pinchStartTimeMs = 0L
        var pinchActive = false

        var twoFingerTapIndexTimeMs = 0L
        var twoFingerTapMiddleTimeMs = 0L
        var twoFingerTapTriggered = false

        var swipeStartX = 0f
        var swipeStartTimeMs = 0L
        var swipeActive = false

        var threeFingerSwipeStartPositions = mapOf<FingerType, PointF>()
        var threeFingerSwipeStartTimeMs = 0L
        var threeFingerSwipeActive = false

        var lastGestureTimeMs = 0L
        var lastGestureType: GestureType? = null
    }

    fun detectGesture(
        fingers: Map<FingerType, TrackedFinger>,
        history: List<FrameSnapshot>,
    ): GestureResult? {
        val now = System.currentTimeMillis()

        val recentHistory = if (history.isNotEmpty()) {
            history.takeLast(MAX_FRAME_HISTORY)
        } else {
            frameHistory.toList().takeLast(MAX_FRAME_HISTORY)
        }

        if (recentHistory.size < 2) return null

        val result = detectThumbTap(fingers, recentHistory, now) ?:
            detectPinch(fingers, recentHistory, now) ?:
            detectTwoFingerTap(fingers, recentHistory, now) ?:
            detectSwipe(fingers, recentHistory, now) ?:
            detectThreeFingerSwipe(fingers, recentHistory, now)

        if (result != null) {
            val timeSinceLastGesture = now - gestureState.lastGestureTimeMs
            if (result.gesture == gestureState.lastGestureType && timeSinceLastGesture < GESTURE_COOLDOWN_MS) {
                return null
            }
            gestureState.lastGestureTimeMs = now
            gestureState.lastGestureType = result.gesture
        }

        return result
    }

    fun addFrameSnapshot(snapshot: FrameSnapshot) {
        frameHistory.addLast(snapshot)
        while (frameHistory.size > MAX_FRAME_HISTORY + 1) {
            frameHistory.removeFirst()
        }
    }

    private fun detectThumbTap(
        fingers: Map<FingerType, TrackedFinger>,
        history: List<FrameSnapshot>,
        now: Long,
    ): GestureResult? {
        val thumb = fingers[FingerType.THUMB] ?: return null

        if (!gestureState.thumbTapActive) {
            if (thumb.velocity.y > THUMB_TAP_DOWN_VELOCITY &&
                thumb.speed < THUMB_TAP_MAX_SPEED
            ) {
                gestureState.thumbTapStartY = thumb.position.y
                gestureState.thumbTapStartTimeMs = now
                gestureState.thumbTapActive = true
            }
            return null
        }

        val elapsed = now - gestureState.thumbTapStartTimeMs
        if (elapsed > THUMB_TAP_MAX_DURATION_MS) {
            gestureState.thumbTapActive = false
            return null
        }

        val totalDisplacement = abs(thumb.position.y - gestureState.thumbTapStartY)
        if (totalDisplacement < THUMB_TAP_MIN_DISPLACEMENT) {
            return null
        }

        if (thumb.velocity.y < -THUMB_TAP_UP_VELOCITY && totalDisplacement < THUMB_TAP_MAX_DISPLACEMENT) {
            gestureState.thumbTapActive = false

            val confidence = computeThumbTapConfidence(
                displacement = totalDisplacement,
                duration = elapsed,
                speed = thumb.speed,
            )

            if (confidence >= THUMB_TAP_CONFIDENCE_THRESHOLD) {
                return GestureResult(
                    gesture = GestureType.TAP,
                    confidence = confidence,
                    position = PointF(thumb.position.x, thumb.position.y),
                )
            }
        }

        return null
    }

    private fun computeThumbTapConfidence(
        displacement: Float,
        duration: Long,
        speed: Float,
    ): Float {
        val displacementScore = when {
            displacement in THUMB_TAP_MIN_DISPLACEMENT..THUMB_TAP_MAX_DISPLACEMENT -> 1.0f
            displacement < THUMB_TAP_MIN_DISPLACEMENT -> displacement / THUMB_TAP_MIN_DISPLACEMENT
            else -> (1f - (displacement - THUMB_TAP_MAX_DISPLACEMENT) / THUMB_TAP_MAX_DISPLACEMENT).coerceAtLeast(0.3f)
        }

        val durationScore = when {
            duration < 50 -> 0.5f
            duration in 50..200 -> 1.0f
            duration in 200..THUMB_TAP_MAX_DURATION_MS -> 0.7f
            else -> 0.3f
        }

        val speedScore = when {
            speed < 50f -> 0.3f
            speed < 200f -> 0.8f
            speed < 400f -> 1.0f
            else -> 0.6f
        }

        return (displacementScore * 0.4f + durationScore * 0.35f + speedScore * 0.25f)
            .coerceIn(0f, 1f)
    }

    private fun detectPinch(
        fingers: Map<FingerType, TrackedFinger>,
        history: List<FrameSnapshot>,
        now: Long,
    ): GestureResult? {
        val thumb = fingers[FingerType.THUMB] ?: return null
        val index = fingers[FingerType.INDEX] ?: return null

        val currentDistance = thumb.position.distanceTo(index.position)

        if (!gestureState.pinchActive) {
            if (currentDistance > PINCH_OPEN_THRESHOLD) {
                gestureState.pinchStartDistance = currentDistance
                gestureState.pinchStartTimeMs = now
                gestureState.pinchActive = true
            }
            return null
        }

        val elapsed = now - gestureState.pinchStartTimeMs
        if (elapsed > PINCH_MAX_DURATION_MS) {
            gestureState.pinchActive = false
            return null
        }

        if (currentDistance < PINCH_CLOSE_THRESHOLD) {
            val ratio = currentDistance / gestureState.pinchStartDistance
            if (ratio < PINCH_CLOSE_RATIO) {
                gestureState.pinchActive = false

                val center = PointF(
                    (thumb.position.x + index.position.x) / 2f,
                    (thumb.position.y + index.position.y) / 2f,
                )

                val confidence = computePinchConfidence(
                    startDistance = gestureState.pinchStartDistance,
                    endDistance = currentDistance,
                    duration = elapsed,
                )

                if (confidence >= PINCH_CONFIDENCE_THRESHOLD) {
                    return GestureResult(
                        gesture = GestureType.PINCH,
                        confidence = confidence,
                        position = center,
                    )
                }
            }
        }

        return null
    }

    private fun computePinchConfidence(
        startDistance: Float,
        endDistance: Float,
        duration: Long,
    ): Float {
        val compressionRatio = if (startDistance > 0f) endDistance / startDistance else 1f
        val compressionScore = (1f - compressionRatio).coerceIn(0f, 1f)

        val durationScore = when {
            duration < 100 -> 0.5f
            duration in 100..400 -> 1.0f
            duration in 400..PINCH_MAX_DURATION_MS -> 0.7f
            else -> 0.3f
        }

        val distanceScore = when {
            startDistance > 150f -> 0.9f
            startDistance > 80f -> 1.0f
            startDistance > 30f -> 0.7f
            else -> 0.3f
        }

        return (compressionScore * 0.5f + durationScore * 0.25f + distanceScore * 0.25f)
            .coerceIn(0f, 1f)
    }

    private fun detectTwoFingerTap(
        fingers: Map<FingerType, TrackedFinger>,
        history: List<FrameSnapshot>,
        now: Long,
    ): GestureResult? {
        if (gestureState.twoFingerTapTriggered) {
            if (now - gestureState.twoFingerTapIndexTimeMs > TWO_FINGER_TAP_COOLDOWN_MS) {
                gestureState.twoFingerTapTriggered = false
            } else {
                return null
            }
        }

        val index = fingers[FingerType.INDEX] ?: return null
        val middle = fingers[FingerType.MIDDLE] ?: return null

        val indexTapDetected = detectFingerTap(index, history)
        val middleTapDetected = detectFingerTap(middle, history)

        if (indexTapDetected) {
            gestureState.twoFingerTapIndexTimeMs = now
        }
        if (middleTapDetected) {
            gestureState.twoFingerTapMiddleTimeMs = now
        }

        val indexTime = gestureState.twoFingerTapIndexTimeMs
        val middleTime = gestureState.twoFingerTapMiddleTimeMs

        if (indexTime > 0 && middleTime > 0) {
            val timeDiff = abs(indexTime - middleTime)
            if (timeDiff <= TWO_FINGER_TAP_MAX_INTERVAL_MS) {
                gestureState.twoFingerTapTriggered = true
                gestureState.twoFingerTapIndexTimeMs = 0
                gestureState.twoFingerTapMiddleTimeMs = 0

                val center = PointF(
                    (index.position.x + middle.position.x) / 2f,
                    (index.position.y + middle.position.y) / 2f,
                )

                val confidence = computeTwoFingerTapConfidence(timeDiff, index, middle)
                if (confidence >= TWO_FINGER_TAP_CONFIDENCE_THRESHOLD) {
                    return GestureResult(
                        gesture = GestureType.TWO_FINGER_TAP,
                        confidence = confidence,
                        position = center,
                    )
                }
            }
        }

        return null
    }

    private fun detectFingerTap(
        finger: TrackedFinger,
        history: List<FrameSnapshot>,
    ): Boolean {
        if (history.size < 2) return false

        val current = history.last().fingers[finger.type] ?: return false
        val previous = history[history.size - 2].fingers[finger.type] ?: return false

        val wasMovingDown = previous.velocity.y > TAP_DOWN_VELOCITY_THRESHOLD
        val isSlowingDown = current.velocity.y < previous.velocity.y * 0.5f
        val smallDisplacement = abs(current.position.y - (history.first().fingers[finger.type]?.position?.y ?: current.position.y)) < TAP_MAX_DISPLACEMENT

        return wasMovingDown && isSlowingDown && smallDisplacement
    }

    private fun computeTwoFingerTapConfidence(
        timeDiffMs: Long,
        index: TrackedFinger,
        middle: TrackedFinger,
    ): Float {
        val timingScore = 1f - (timeDiffMs.toFloat() / TWO_FINGER_TAP_MAX_INTERVAL_MS)

        val indexSpeed = index.speed
        val middleSpeed = middle.speed
        val speedBalance = 1f - abs(indexSpeed - middleSpeed) / (indexSpeed + middleSpeed + 1f)

        val avgSpeed = (indexSpeed + middleSpeed) / 2f
        val speedScore = when {
            avgSpeed in 30f..150f -> 1.0f
            avgSpeed < 30f -> avgSpeed / 30f
            avgSpeed < 300f -> 0.8f
            else -> 0.5f
        }

        return (timingScore * 0.4f + speedBalance * 0.3f + speedScore * 0.3f)
            .coerceIn(0f, 1f)
    }

    private fun detectSwipe(
        fingers: Map<FingerType, TrackedFinger>,
        history: List<FrameSnapshot>,
        now: Long,
    ): GestureResult? {
        val index = fingers[FingerType.INDEX] ?: return null

        if (!gestureState.swipeActive) {
            if (index.speed > SWIPE_MIN_SPEED) {
                gestureState.swipeStartX = index.position.x
                gestureState.swipeStartTimeMs = now
                gestureState.swipeActive = true
            }
            return null
        }

        val elapsed = now - gestureState.swipeStartTimeMs
        if (elapsed > SWIPE_MAX_DURATION_MS) {
            gestureState.swipeActive = false
            return null
        }

        val horizontalDisplacement = index.position.x - gestureState.swipeStartX
        val absDisplacement = abs(horizontalDisplacement)

        if (absDisplacement > SWIPE_MIN_DISTANCE) {
            gestureState.swipeActive = false

            val isHorizontal = abs(index.velocity.x) > abs(index.velocity.y) * SWIPE_HORIZONTAL_RATIO
            if (!isHorizontal) return null

            val direction = if (horizontalDisplacement > 0) GestureType.SWIPE_RIGHT else GestureType.SWIPE_LEFT
            val confidence = computeSwipeConfidence(
                displacement = absDisplacement,
                duration = elapsed,
                speed = index.speed,
                velocityRatio = abs(index.velocity.x) / (abs(index.velocity.y) + 1f),
            )

            if (confidence >= SWIPE_CONFIDENCE_THRESHOLD) {
                return GestureResult(
                    gesture = direction,
                    confidence = confidence,
                    position = PointF(index.position.x, index.position.y),
                )
            }
        }

        return null
    }

    private fun computeSwipeConfidence(
        displacement: Float,
        duration: Long,
        speed: Float,
        velocityRatio: Float,
    ): Float {
        val distanceScore = when {
            displacement > SWIPE_MIN_DISTANCE * 3f -> 1.0f
            displacement > SWIPE_MIN_DISTANCE * 2f -> 0.9f
            displacement > SWIPE_MIN_DISTANCE -> 0.7f
            else -> 0.4f
        }

        val durationScore = when {
            duration < 100 -> 0.6f
            duration in 100..SWIPE_MAX_DURATION_MS -> 1.0f
            else -> 0.5f
        }

        val speedScore = when {
            speed > 500f -> 1.0f
            speed > 200f -> 0.8f
            speed > SWIPE_MIN_SPEED -> 0.6f
            else -> 0.3f
        }

        val horizontalityScore = velocityRatio.coerceAtMost(3f) / 3f

        return (distanceScore * 0.3f + durationScore * 0.2f + speedScore * 0.3f + horizontalityScore * 0.2f)
            .coerceIn(0f, 1f)
    }

    private fun detectThreeFingerSwipe(
        fingers: Map<FingerType, TrackedFinger>,
        history: List<FrameSnapshot>,
        now: Long,
    ): GestureResult? {
        val index = fingers[FingerType.INDEX] ?: return null
        val middle = fingers[FingerType.MIDDLE] ?: return null
        val ring = fingers[FingerType.RING] ?: return null

        if (!gestureState.threeFingerSwipeActive) {
            val allMoving = index.speed > THREE_FINGER_MIN_SPEED &&
                middle.speed > THREE_FINGER_MIN_SPEED &&
                ring.speed > THREE_FINGER_MIN_SPEED

            if (allMoving) {
                gestureState.threeFingerSwipeStartPositions = mapOf(
                    FingerType.INDEX to PointF(index.position.x, index.position.y),
                    FingerType.MIDDLE to PointF(middle.position.x, middle.position.y),
                    FingerType.RING to PointF(ring.position.x, ring.position.y),
                )
                gestureState.threeFingerSwipeStartTimeMs = now
                gestureState.threeFingerSwipeActive = true
            }
            return null
        }

        val elapsed = now - gestureState.threeFingerSwipeStartTimeMs
        if (elapsed > THREE_FINGER_SWIPE_MAX_DURATION_MS) {
            gestureState.threeFingerSwipeActive = false
            return null
        }

        val startPositions = gestureState.threeFingerSwipeStartPositions
        val startIndex = startPositions[FingerType.INDEX] ?: return null
        val startMiddle = startPositions[FingerType.MIDDLE] ?: return null
        val startRing = startPositions[FingerType.RING] ?: return null

        val indexDx = index.position.x - startIndex.x
        val middleDx = middle.position.x - startMiddle.x
        val ringDx = ring.position.x - startRing.x

        val allSameDirection = (indexDx > 0 && middleDx > 0 && ringDx > 0) ||
            (indexDx < 0 && middleDx < 0 && ringDx < 0)

        if (!allSameDirection) {
            gestureState.threeFingerSwipeActive = false
            return null
        }

        val avgDisplacement = (abs(indexDx) + abs(middleDx) + abs(ringDx)) / 3f
        if (avgDisplacement > THREE_FINGER_SWIPE_MIN_DISTANCE) {
            gestureState.threeFingerSwipeActive = false

            val confidence = computeThreeFingerSwipeConfidence(
                displacement = avgDisplacement,
                duration = elapsed,
                consistency = computeDirectionConsistency(indexDx, middleDx, ringDx),
            )

            if (confidence >= THREE_FINGER_SWIPE_CONFIDENCE_THRESHOLD) {
                val direction = if (indexDx > 0) GestureType.SWIPE_RIGHT else GestureType.SWIPE_LEFT
                val center = PointF(
                    (index.position.x + middle.position.x + ring.position.x) / 3f,
                    (index.position.y + middle.position.y + ring.position.y) / 3f,
                )

                return GestureResult(
                    gesture = direction,
                    confidence = confidence,
                    position = center,
                )
            }
        }

        return null
    }

    private fun computeDirectionConsistency(dx1: Float, dx2: Float, dx3: Float): Float {
        val signs = listOf(dx1, dx2, dx3).map { if (it > 0) 1f else -1f }
        val sameSignCount = signs.count { it == signs[0] }
        return sameSignCount / 3f
    }

    private fun computeThreeFingerSwipeConfidence(
        displacement: Float,
        duration: Long,
        consistency: Float,
    ): Float {
        val distanceScore = (displacement / THREE_FINGER_SWIPE_MIN_DISTANCE).coerceAtMost(1.5f) / 1.5f
        val durationScore = when {
            duration < 100 -> 0.6f
            duration in 100..THREE_FINGER_SWIPE_MAX_DURATION_MS -> 1.0f
            else -> 0.5f
        }

        return (distanceScore * 0.4f + durationScore * 0.3f + consistency * 0.3f)
            .coerceIn(0f, 1f)
    }

    fun reset() {
        frameHistory.clear()
        gestureState = InternalGestureState()
    }

    fun clearHistory() {
        frameHistory.clear()
    }

    private fun PointF.distanceTo(other: PointF): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        const val MAX_FRAME_HISTORY = 15
        const val GESTURE_COOLDOWN_MS = 300L

        const val THUMB_TAP_DOWN_VELOCITY = 100f
        const val THUMB_TAP_UP_VELOCITY = 80f
        const val THUMB_TAP_MAX_SPEED = 400f
        const val THUMB_TAP_MIN_DISPLACEMENT = 15f
        const val THUMB_TAP_MAX_DISPLACEMENT = 120f
        const val THUMB_TAP_MAX_DURATION_MS = 350L
        const val THUMB_TAP_CONFIDENCE_THRESHOLD = 0.5f

        const val PINCH_OPEN_THRESHOLD = 80f
        const val PINCH_CLOSE_THRESHOLD = 40f
        const val PINCH_CLOSE_RATIO = 0.5f
        const val PINCH_MAX_DURATION_MS = 500L
        const val PINCH_CONFIDENCE_THRESHOLD = 0.5f

        const val TWO_FINGER_TAP_MAX_INTERVAL_MS = 100L
        const val TWO_FINGER_TAP_COOLDOWN_MS = 500L
        const val TWO_FINGER_TAP_CONFIDENCE_THRESHOLD = 0.5f
        const val TAP_DOWN_VELOCITY_THRESHOLD = 50f
        const val TAP_MAX_DISPLACEMENT = 80f

        const val SWIPE_MIN_DISTANCE = 80f
        const val SWIPE_MIN_SPEED = 100f
        const val SWIPE_MAX_DURATION_MS = 400L
        const val SWIPE_HORIZONTAL_RATIO = 1.5f
        const val SWIPE_CONFIDENCE_THRESHOLD = 0.5f

        const val THREE_FINGER_MIN_SPEED = 80f
        const val THREE_FINGER_SWIPE_MIN_DISTANCE = 100f
        const val THREE_FINGER_SWIPE_MAX_DURATION_MS = 500L
        const val THREE_FINGER_SWIPE_CONFIDENCE_THRESHOLD = 0.5f
    }
}
