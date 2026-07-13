package com.visionboard.domain.usecase.calibration

import com.visionboard.domain.model.CalibrationDomain
import com.visionboard.domain.repository.CalibrationRepositoryInterface
import javax.inject.Inject

class SaveCalibrationUseCase @Inject constructor(
    private val repository: CalibrationRepositoryInterface,
) {
    suspend operator fun invoke(calibration: CalibrationDomain): Result<Long> {
        val validation = calibration.validate()
        if (validation.isFailure) {
            return Result.failure(validation.exceptionOrNull()!!)
        }

        return try {
            val id = repository.saveCalibration(calibration)
            if (calibration.isActive) {
                repository.setActive(id)
            }
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
