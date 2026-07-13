package com.visionboard.domain.usecase.settings

import com.visionboard.domain.model.AppSettingsDomain
import com.visionboard.domain.repository.SettingsRepositoryInterface
import javax.inject.Inject

class UpdateSettingsUseCase @Inject constructor(
    private val repository: SettingsRepositoryInterface,
) {
    suspend operator fun invoke(settings: AppSettingsDomain): Result<Unit> {
        return try {
            val validated = settings.validate()
            repository.updateSettings(validated)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
