package com.visionboard.domain.usecase.settings

import com.visionboard.domain.model.AppSettingsDomain
import com.visionboard.domain.repository.SettingsRepositoryInterface
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSettingsUseCase @Inject constructor(
    private val repository: SettingsRepositoryInterface,
) {
    operator fun invoke(): Flow<AppSettingsDomain> {
        return repository.getSettings()
    }
}
