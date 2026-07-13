package com.visionboard.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionboard.settings.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val currentPage: Int = 0,
    val isLastPage: Boolean = false,
    val isCompleted: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val totalPages = 4

    fun updatePage(page: Int) {
        _uiState.value = _uiState.value.copy(
            currentPage = page,
            isLastPage = page == totalPages - 1
        )
    }

    fun nextPage() {
        val current = _uiState.value.currentPage
        if (current < totalPages - 1) {
            val next = current + 1
            _uiState.value = _uiState.value.copy(
                currentPage = next,
                isLastPage = next == totalPages - 1
            )
        }
    }

    fun skip() {
        completeOnboarding()
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("visionboard_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
            _uiState.value = _uiState.value.copy(isCompleted = true)
        }
    }

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
