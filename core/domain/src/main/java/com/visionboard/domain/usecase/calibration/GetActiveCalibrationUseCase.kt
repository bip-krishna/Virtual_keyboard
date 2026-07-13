package com.visionboard.domain.usecase.calibration

import com.visionboard.domain.model.CalibrationDomain
import com.visionboard.domain.repository.CalibrationRepositoryInterface
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetActiveCalibrationUseCase @Inject constructor(
    private val repository: CalibrationRepositoryInterface,
) {
    operator fun invoke(): Flow<CalibrationDomain?> {
        return repository.getActiveCalibration()
    }
}
