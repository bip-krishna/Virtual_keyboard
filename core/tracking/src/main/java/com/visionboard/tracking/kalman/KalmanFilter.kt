package com.visionboard.tracking.kalman

import android.graphics.PointF
import com.visionboard.common.constants.AppConstants.Kalman.DEFAULT_DELTA_TIME
import com.visionboard.common.constants.AppConstants.Kalman.MEASUREMENT_DIMENSION
import com.visionboard.common.constants.AppConstants.Kalman.MEASUREMENT_NOISE
import com.visionboard.common.constants.AppConstants.Kalman.MIN_CONFIDENCE_FOR_UPDATE
import com.visionboard.common.constants.AppConstants.Kalman.PROCESS_NOISE
import com.visionboard.common.constants.AppConstants.Kalman.STATE_DIMENSION

class FloatMatrix(val rows: Int, val cols: Int) {

    val data = FloatArray(rows * cols)

    constructor(values: Array<FloatArray>) : this(values.size, values[0].size) {
        for (i in values.indices) {
            for (j in values[i].indices) {
                data[i * cols + j] = values[i][j]
            }
        }
    }

    operator fun get(row: Int, col: Int): Float = data[row * cols + col]

    operator fun set(row: Int, col: Int, value: Float) {
        data[row * cols + col] = value
    }

    fun getRow(row: Int): FloatArray {
        val result = FloatArray(cols)
        for (j in 0 until cols) {
            result[j] = data[row * cols + j]
        }
        return result
    }

    fun getCol(col: Int): FloatArray {
        val result = FloatArray(rows)
        for (i in 0 until rows) {
            result[i] = data[i * cols + col]
        }
        return result
    }

    fun copy(): FloatMatrix {
        val m = FloatMatrix(rows, cols)
        System.arraycopy(data, 0, m.data, 0, data.size)
        return m
    }

    fun multiply(other: FloatMatrix): FloatMatrix {
        require(cols == other.rows) {
            "Matrix dimensions mismatch: ${rows}x${cols} * ${other.rows}x${other.cols}"
        }
        val result = FloatMatrix(rows, other.cols)
        for (i in 0 until rows) {
            for (j in 0 until other.cols) {
                var sum = 0f
                for (k in 0 until cols) {
                    sum += this[i, k] * other[k, j]
                }
                result[i, j] = sum
            }
        }
        return result
    }

    fun add(other: FloatMatrix): FloatMatrix {
        require(rows == other.rows && cols == other.cols) {
            "Matrix dimensions mismatch for addition: ${rows}x${cols} + ${other.rows}x${other.cols}"
        }
        val result = FloatMatrix(rows, cols)
        for (i in data.indices) {
            result.data[i] = data[i] + other.data[i]
        }
        return result
    }

    fun subtract(other: FloatMatrix): FloatMatrix {
        require(rows == other.rows && cols == other.cols) {
            "Matrix dimensions mismatch for subtraction: ${rows}x${cols} - ${other.rows}x${other.cols}"
        }
        val result = FloatMatrix(rows, cols)
        for (i in data.indices) {
            result.data[i] = data[i] - other.data[i]
        }
        return result
    }

    fun transpose(): FloatMatrix {
        val result = FloatMatrix(cols, rows)
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                result[j, i] = this[i, j]
            }
        }
        return result
    }

    fun scale(scalar: Float): FloatMatrix {
        val result = FloatMatrix(rows, cols)
        for (i in data.indices) {
            result.data[i] = data[i] * scalar
        }
        return result
    }

    fun invert(): FloatMatrix {
        require(rows == cols) { "Matrix must be square to invert, got ${rows}x${cols}" }
        val n = rows
        val augmented = FloatMatrix(n, 2 * n)

        for (i in 0 until n) {
            for (j in 0 until n) {
                augmented[i, j] = this[i, j]
            }
            augmented[i, n + i] = 1f
        }

        for (col in 0 until n) {
            var maxRow = col
            var maxVal = Math.abs(augmented[col, col])
            for (row in col + 1 until n) {
                val absVal = Math.abs(augmented[row, col])
                if (absVal > maxVal) {
                    maxVal = absVal
                    maxRow = row
                }
            }
            if (maxVal < 1e-10f) {
                throw ArithmeticException("Matrix is singular and cannot be inverted")
            }
            if (maxRow != col) {
                for (j in 0 until 2 * n) {
                    val tmp = augmented[col, j]
                    augmented[col, j] = augmented[maxRow, j]
                    augmented[maxRow, j] = tmp
                }
            }

            val pivot = augmented[col, col]
            for (j in 0 until 2 * n) {
                augmented[col, j] /= pivot
            }

            for (row in 0 until n) {
                if (row == col) continue
                val factor = augmented[row, col]
                for (j in 0 until 2 * n) {
                    augmented[row, j] -= factor * augmented[col, j]
                }
            }
        }

        val result = FloatMatrix(n, n)
        for (i in 0 until n) {
            for (j in 0 until n) {
                result[i, j] = augmented[i, n + j]
            }
        }
        return result
    }

    fun toArray(): Array<FloatArray> {
        val result = Array(rows) { FloatArray(cols) }
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                result[i][j] = this[i, j]
            }
        }
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (i in 0 until rows) {
            sb.append("[ ")
            for (j in 0 until cols) {
                sb.append(String.format("%8.4f ", this[i, j]))
            }
            sb.append("]\n")
        }
        return sb.toString()
    }

    companion object {
        fun identity(size: Int): FloatMatrix {
            val m = FloatMatrix(size, size)
            for (i in 0 until size) {
                m[i, i] = 1f
            }
            return m
        }

        fun zeros(rows: Int, cols: Int): FloatMatrix = FloatMatrix(rows, cols)
    }
}

data class KalmanState(
    val position: PointF,
    val velocity: PointF,
    val covariance: FloatMatrix,
)

class KalmanFilter {

    private val stateDimension = STATE_DIMENSION
    private val measurementDimension = MEASUREMENT_DIMENSION

    private var state = FloatMatrix(stateDimension, 1)
    private var covariance = FloatMatrix(stateDimension, stateDimension)

    private var transitionMatrix = FloatMatrix(Array(stateDimension) { FloatArray(stateDimension) })
    private var measurementMatrix = FloatMatrix(Array(measurementDimension) { FloatArray(stateDimension) })
    private var processNoise = FloatMatrix(stateDimension, stateDimension)
    private var measurementNoise = FloatMatrix(measurementDimension, measurementDimension)

    private var initialized = false
    private var lastTimestamp = 0L

    init {
        initializeMatrices()
    }

    private fun initializeMatrices() {
        for (i in 0 until stateDimension) {
            for (j in 0 until stateDimension) {
                transitionMatrix[i, j] = 0f
            }
        }
        transitionMatrix[0, 0] = 1f
        transitionMatrix[1, 1] = 1f
        transitionMatrix[2, 2] = 1f
        transitionMatrix[3, 3] = 1f

        for (i in 0 until measurementDimension) {
            for (j in 0 until stateDimension) {
                measurementMatrix[i, j] = 0f
            }
        }
        measurementMatrix[0, 0] = 1f
        measurementMatrix[1, 1] = 1f

        covariance = FloatMatrix.identity(stateDimension).scale(1000f)
    }

    private fun updateTransitionMatrix(dt: Float) {
        transitionMatrix[0, 2] = dt
        transitionMatrix[1, 3] = dt
    }

    private fun updateProcessNoise(dt: Float) {
        val dt2 = dt * dt
        val dt3 = dt2 * dt / 2f
        val dt4 = dt2 * dt2 / 4f
        val q = PROCESS_NOISE

        processNoise[0, 0] = dt4 * q
        processNoise[0, 2] = dt3 * q
        processNoise[1, 1] = dt4 * q
        processNoise[1, 3] = dt3 * q
        processNoise[2, 0] = dt3 * q
        processNoise[2, 2] = dt2 * q
        processNoise[3, 1] = dt3 * q
        processNoise[3, 3] = dt2 * q
    }

    private fun updateMeasurementNoise(confidence: Float) {
        val baseNoise = MEASUREMENT_NOISE
        val adjustedNoise = if (confidence > 0f) {
            baseNoise / confidence.coerceAtLeast(0.1f)
        } else {
            baseNoise * 10f
        }

        measurementNoise[0, 0] = adjustedNoise
        measurementNoise[1, 1] = adjustedNoise
    }

    fun predict(dt: Float): PointF {
        if (!initialized) return PointF(state[0, 0], state[1, 0])

        val clampedDt = dt.coerceIn(0.001f, 0.5f)
        updateTransitionMatrix(clampedDt)
        updateProcessNoise(clampedDt)

        state = transitionMatrix.multiply(state)
        covariance = transitionMatrix
            .multiply(covariance)
            .multiply(transitionMatrix.transpose())
            .add(processNoise)

        return PointF(state[0, 0], state[1, 0])
    }

    fun update(measurement: PointF, confidence: Float): PointF {
        if (!initialized) {
            state[0, 0] = measurement.x
            state[1, 0] = measurement.y
            state[2, 0] = 0f
            state[3, 0] = 0f
            initialized = true
            return PointF(measurement.x, measurement.y)
        }

        if (confidence < MIN_CONFIDENCE_FOR_UPDATE) {
            return PointF(state[0, 0], state[1, 0])
        }

        updateMeasurementNoise(confidence)

        val z = FloatMatrix(measurementDimension, 1)
        z[0, 0] = measurement.x
        z[1, 0] = measurement.y

        val H = measurementMatrix
        val Ht = H.transpose()

        val S = H.multiply(covariance).multiply(Ht).add(measurementNoise)

        val Sinv = try {
            S.invert()
        } catch (e: ArithmeticException) {
            return PointF(state[0, 0], state[1, 0])
        }

        val K = covariance.multiply(Ht).multiply(Sinv)

        val innovation = z.subtract(H.multiply(state))
        state = state.add(K.multiply(innovation))

        val I = FloatMatrix.identity(stateDimension)
        val IKH = I.subtract(K.multiply(H))
        covariance = IKH.multiply(covariance)

        state[2, 0] = state[2, 0].coerceIn(-3000f, 3000f)
        state[3, 0] = state[3, 0].coerceIn(-3000f, 3000f)

        return PointF(state[0, 0], state[1, 0])
    }

    fun getState(): KalmanState {
        return KalmanState(
            position = PointF(state[0, 0], state[1, 0]),
            velocity = PointF(state[2, 0], state[3, 0]),
            covariance = covariance.copy(),
        )
    }

    fun reset() {
        state = FloatMatrix(stateDimension, 1)
        covariance = FloatMatrix.identity(stateDimension).scale(1000f)
        initialized = false
        lastTimestamp = 0L
        initializeMatrices()
    }

    fun isInitialized(): Boolean = initialized
}
