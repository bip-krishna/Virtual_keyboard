package com.visionboard.tracking.kalman

import android.graphics.PointF
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

@Singleton
class TemporalSmoother @Inject constructor() {

    private var positionAlpha = DEFAULT_POSITION_ALPHA
    private var velocityAlpha = DEFAULT_VELOCITY_ALPHA

    private var smoothedPosition: PointF? = null
    private var smoothedVelocity: PointF? = null
    private var lastTimestamp = 0L
    private var initialized = false

    fun smooth(raw: PointF, confidence: Float): PointF {
        val alpha = positionAlpha * confidence.coerceIn(0f, 1f)

        if (!initialized) {
            smoothedPosition = PointF(raw.x, raw.y)
            initialized = true
            return PointF(raw.x, raw.y)
        }

        val prev = smoothedPosition!!
        val smoothed = PointF(
            prev.x + alpha * (raw.x - prev.x),
            prev.y + alpha * (raw.y - prev.y),
        )
        smoothedPosition = smoothed
        return PointF(smoothed.x, smoothed.y)
    }

    fun smoothVelocity(raw: PointF): PointF {
        if (!initialized) {
            smoothedVelocity = PointF(raw.x, raw.y)
            return PointF(raw.x, raw.y)
        }

        val prev = smoothedVelocity ?: PointF(0f, 0f)
        val alpha = velocityAlpha
        val smoothed = PointF(
            prev.x + alpha * (raw.x - prev.x),
            prev.y + alpha * (raw.y - prev.y),
        )
        smoothedVelocity = smoothed
        return PointF(smoothed.x, smoothed.y)
    }

    fun smoothWithTimestamp(raw: PointF, confidence: Float, timestampMs: Long): PointF {
        val alpha = positionAlpha * confidence.coerceIn(0f, 1f)

        if (!initialized) {
            smoothedPosition = PointF(raw.x, raw.y)
            lastTimestamp = timestampMs
            initialized = true
            return PointF(raw.x, raw.y)
        }

        val dtMs = (timestampMs - lastTimestamp).coerceIn(1L, 500L)
        val timeFactor = (dtMs / 33f).coerceIn(0.5f, 3f)
        val adaptiveAlpha = (alpha * timeFactor).coerceAtMost(1f)

        val prev = smoothedPosition!!
        val smoothed = PointF(
            prev.x + adaptiveAlpha * (raw.x - prev.x),
            prev.y + adaptiveAlpha * (raw.y - prev.y),
        )

        smoothedPosition = smoothed
        lastTimestamp = timestampMs
        return PointF(smoothed.x, smoothed.y)
    }

    fun getSmoothedVelocity(raw: PointF, dtMs: Long): PointF {
        if (dtMs <= 0) return smoothedVelocity ?: PointF(0f, 0f)

        val dt = (dtMs / 1000f).coerceAtLeast(0.001f)
        val instantVelocity = PointF(raw.x / dt, raw.y / dt)
        return smoothVelocity(instantVelocity)
    }

    fun reset() {
        smoothedPosition = null
        smoothedVelocity = null
        lastTimestamp = 0L
        initialized = false
    }

    fun setAlpha(positionAlpha: Float, velocityAlpha: Float) {
        this.positionAlpha = positionAlpha.coerceIn(0.01f, 1f)
        this.velocityAlpha = velocityAlpha.coerceIn(0.01f, 1f)
    }

    fun getPositionAlpha(): Float = positionAlpha
    fun getVelocityAlpha(): Float = velocityAlpha

    fun isInitialized(): Boolean = initialized

    fun getSmoothedPosition(): PointF? = smoothedPosition?.let { PointF(it.x, it.y) }

    fun getSmoothedVelocityRaw(): PointF? = smoothedVelocity?.let { PointF(it.x, it.y) }

    companion object {
        const val DEFAULT_POSITION_ALPHA = 0.3f
        const val DEFAULT_VELOCITY_ALPHA = 0.5f
    }
}
