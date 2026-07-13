package com.visionboard.utils.pool

import org.opencv.core.CvType
import org.opencv.core.Mat
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class PoolStats(
    val poolSize: Int,
    val activeCount: Int,
    val totalCreated: Int,
    val totalAcquired: Long,
    val totalReleased: Long,
    val totalAutoRecycled: Long,
    val maxSize: Int,
) {
    val utilization: Float
        get() = if (maxSize > 0) activeCount.toFloat() / maxSize else 0f

    fun toSummary(): String =
        "Pool[size=$poolSize, active=$activeCount, created=$totalCreated, " +
            "acquired=$totalAcquired, released=$totalReleased, " +
            "autoRecycled=$totalAutoRecycled, max=$maxSize]"
}

class MatPool(
    initialSize: Int = 15,
    val maxSize: Int = 30,
) {
    private val pool = ConcurrentLinkedDeque<PooledMat>()
    private val totalCreated = AtomicInteger(0)
    private val totalAcquired = AtomicLong(0)
    private val totalReleased = AtomicLong(0)
    private val totalAutoRecycled = AtomicLong(0)
    private val activeCount = AtomicInteger(0)

    init {
        preWarm(initialSize, 480, 640, CvType.CV_8UC3)
    }

    fun acquire(rows: Int, cols: Int, type: Int = CvType.CV_8UC3): Mat {
        totalAcquired.incrementAndGet()

        val iterator = pool.iterator()
        while (iterator.hasNext()) {
            val pooled = iterator.next()
            if (pooled.mat.rows() == rows && pooled.mat.cols() == cols && pooled.mat.type() == type) {
                iterator.remove()
                pooled.isInUse = true
                pooled.lastAcquireTime = System.nanoTime()
                activeCount.incrementAndGet()
                return pooled.mat
            }
        }

        val mat = Mat(rows, cols, type)
        totalCreated.incrementAndGet()
        activeCount.incrementAndGet()
        return mat
    }

    fun release(mat: Mat) {
        if (activeCount.get() > 0) {
            activeCount.decrementAndGet()
        }
        totalReleased.incrementAndGet()

        if (pool.size < maxSize && !mat.empty() && mat.isContinuous) {
            try {
                mat.setTo(org.opencv.core.Scalar(0.0))
                val pooled = PooledMat(mat = mat, isInUse = true, lastAcquireTime = System.nanoTime())
                pooled.isInUse = false
                pool.addLast(pooled)
            } catch (_: Exception) {
                mat.release()
            }
        } else {
            if (!mat.empty()) {
                totalAutoRecycled.incrementAndGet()
            }
            mat.release()
        }
    }

    fun preWarm(count: Int, rows: Int, cols: Int, type: Int) {
        val toAdd = (count - pool.size).coerceAtLeast(0)
        repeat(toAdd) {
            if (totalCreated.get() < maxSize) {
                val mat = Mat(rows, cols, type)
                val pooled = PooledMat(mat = mat, isInUse = false, lastAcquireTime = 0L)
                pool.addLast(pooled)
                totalCreated.incrementAndGet()
            }
        }
    }

    fun preWarm(count: Int) {
        preWarm(count, 480, 640, CvType.CV_8UC3)
    }

    fun getStats(): PoolStats = PoolStats(
        poolSize = pool.size,
        activeCount = activeCount.get(),
        totalCreated = totalCreated.get(),
        totalAcquired = totalAcquired.get(),
        totalReleased = totalReleased.get(),
        totalAutoRecycled = totalAutoRecycled.get(),
        maxSize = maxSize,
    )

    fun clear() {
        val iterator = pool.iterator()
        while (iterator.hasNext()) {
            val pooled = iterator.next()
            pooled.mat.release()
            iterator.remove()
        }
        activeCount.set(0)
    }

    fun trimToSize(targetSize: Int) {
        while (pool.size > targetSize) {
            val removed = pool.pollFirst() ?: break
            removed.mat.release()
            totalAutoRecycled.incrementAndGet()
        }
    }

    fun trimStale(maxAgeMs: Long = 30_000L) {
        val now = System.nanoTime()
        val maxAgeNanos = maxAgeMs * 1_000_000

        val iterator = pool.iterator()
        while (iterator.hasNext()) {
            val pooled = iterator.next()
            if (!pooled.isInUse && (now - pooled.lastAcquireTime) > maxAgeNanos) {
                iterator.remove()
                pooled.mat.release()
                totalAutoRecycled.incrementAndGet()
            }
        }
    }

    fun size(): Int = pool.size

    fun activeCount(): Int = activeCount.get()

    fun availableCount(): Int = pool.size

    inline fun <R> use(rows: Int, cols: Int, type: Int = CvType.CV_8UC3, block: (Mat) -> R): R {
        val mat = acquire(rows, cols, type)
        return try {
            block(mat)
        } finally {
            release(mat)
        }
    }

    private data class PooledMat(
        val mat: Mat,
        @Volatile var isInUse: Boolean = false,
        @Volatile var lastAcquireTime: Long = 0L,
    )
}
