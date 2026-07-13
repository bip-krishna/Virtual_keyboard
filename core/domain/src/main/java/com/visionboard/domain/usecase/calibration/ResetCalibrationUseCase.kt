package com.visionboard.domain.usecase.calibration

import com.visionboard.domain.repository.CalibrationRepositoryInterface
import javax.inject.Inject

class ResetCalibrationUseCase @Inject constructor(
    private val repository: CalibrationRepositoryInterface,
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            repository.deleteAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
