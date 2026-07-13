package com.visionboard.common.utils

import android.graphics.PointF
import android.graphics.RectF
import org.opencv.core.Mat
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

open class ObjectPool<T>(
    private val factory: () -> T,
    private val reset: (T) -> Unit = {},
    initialSize: Int = 10,
    val maxSize: Int = 100,
) {
    private val pool = ConcurrentLinkedDeque<T>()
    private val totalCreated = AtomicInteger(0)
    private val totalAcquired = AtomicLong(0)
    private val totalReleased = AtomicLong(0)
    private val activeCount = AtomicInteger(0)

    init {
        preWarm(initialSize)
    }

    fun acquire(): T {
        totalAcquired.incrementAndGet()
        val obj = pool.pollFirst() ?: run {
            totalCreated.incrementAndGet()
            factory()
        }
        activeCount.incrementAndGet()
        return obj
    }

    fun release(obj: T) {
        if (activeCount.get() > 0) {
            activeCount.decrementAndGet()
        }
        totalReleased.incrementAndGet()
        if (pool.size < maxSize) {
            try {
                reset(obj)
                pool.addLast(obj)
            } catch (_: Exception) {
                // Object cannot be reused, let GC handle it
            }
        }
    }

    fun preWarm(count: Int) {
        val toAdd = (count - pool.size).coerceAtLeast(0)
        repeat(toAdd) {
            if (totalCreated.get() < maxSize) {
                pool.addLast(factory())
                totalCreated.incrementAndGet()
            }
        }
    }

    fun clear() {
        pool.clear()
        activeCount.set(0)
    }

    fun stats(): PoolStats = PoolStats(
        poolSize = pool.size,
        activeCount = activeCount.get(),
        totalCreated = totalCreated.get(),
        totalAcquired = totalAcquired.get(),
        totalReleased = totalReleased.get(),
        maxSize = maxSize,
    )

    data class PoolStats(
        val poolSize: Int,
        val activeCount: Int,
        val totalCreated: Int,
        val totalAcquired: Long,
        val totalReleased: Long,
        val maxSize: Int,
    ) {
        val utilization: Float
            get() = if (maxSize > 0) activeCount.toFloat() / maxSize else 0f
    }

    inline fun <R> use(block: (T) -> R): R {
        val obj = acquire()
        return try {
            block(obj)
        } finally {
            release(obj)
        }
    }
}

class MatPool(initialSize: Int = 15, maxSize: Int = 30) : ObjectPool<Mat>(
    factory = {
        Mat()
    },
    reset = { mat ->
        if (!mat.empty()) {
            mat.setTo(org.opencv.core.Scalar(0.0))
        }
    },
    initialSize = initialSize,
    maxSize = maxSize,
) {
    fun acquire(rows: Int, cols: Int, type: Int = org.opencv.core.CvType.CV_8UC3): Mat {
        val mat = acquire()
        if (mat.rows() != rows || mat.cols() != cols || mat.type() != type) {
            mat.release()
            return Mat(rows, cols, type)
        }
        return mat
    }

    fun acquireGrayscale(width: Int, height: Int): Mat {
        return acquire(height, width, org.opencv.core.CvType.CV_8UC1)
    }

    fun releaseIfValid(mat: Mat) {
        if (!mat.empty() && !mat.isContinuous) {
            release(mat)
        } else if (mat.empty()) {
            // Already empty, just release reference
            release(mat)
        } else {
            release(mat)
        }
    }

    fun warmUp(rows: Int, cols: Int, type: Int, count: Int = 5) {
        repeat(count) {
            val mat = Mat(rows, cols, type)
            release(mat)
        }
    }
}

class PointFPool(initialSize: Int = 50, maxSize: Int = 200) : ObjectPool<PointF>(
    factory = { PointF(0f, 0f) },
    reset = { point ->
        point.x = 0f
        point.y = 0f
    },
    initialSize = initialSize,
    maxSize = maxSize,
) {
    fun acquire(x: Float, y: Float): PointF {
        val point = acquire()
        point.x = x
        point.y = y
        return point
    }
}

class RectPool(initialSize: Int = 20, maxSize: Int = 50) : ObjectPool<RectF>(
    factory = { RectF(0f, 0f, 0f, 0f) },
    reset = { rect ->
        rect.set(0f, 0f, 0f, 0f)
    },
    initialSize = initialSize,
    maxSize = maxSize,
) {
    fun acquire(left: Float, top: Float, right: Float, bottom: Float): RectF {
        val rect = acquire()
        rect.set(left, top, right, bottom)
        return rect
    }

    fun acquireFromPoints(points: List<PointF>): RectF {
        if (points.isEmpty()) return acquire()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (point in points) {
            if (point.x < minX) minX = point.x
            if (point.y < minY) minY = point.y
            if (point.x > maxX) maxX = point.x
            if (point.y > maxY) maxY = point.y
        }
        return acquire(minX, minY, maxX, maxY)
    }
}

object Pools {
    val matPool: MatPool by lazy { MatPool() }
    val pointFPool: PointFPool by lazy { PointFPool() }
    val rectPool: RectPool by lazy { RectPool() }

    fun initialize() {
        matPool.preWarm(15)
        pointFPool.preWarm(50)
        rectPool.preWarm(20)
    }

    fun clearAll() {
        matPool.clear()
        pointFPool.clear()
        rectPool.clear()
    }

    fun stats(): Map<String, ObjectPool.PoolStats> = mapOf(
        "mat" to matPool.stats(),
        "pointf" to pointFPool.stats(),
        "rect" to rectPool.stats(),
    )
}
