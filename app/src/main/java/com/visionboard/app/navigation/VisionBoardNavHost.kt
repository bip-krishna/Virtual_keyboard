package com.visionboard.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.visionboard.ui.calibration.CalibrationScreen
import com.visionboard.ui.calibration.CalibrationViewModel
import com.visionboard.ui.onboarding.OnboardingScreen
import com.visionboard.ui.onboarding.OnboardingViewModel
import com.visionboard.ui.preview.CameraPreviewScreen
import com.visionboard.ui.splash.SplashScreen
import com.visionboard.ui.splash.SplashViewModel
import com.visionboard.ui.typing.TypingScreen

private const val TRANSITION_DURATION = 300

@Composable
fun VisionBoardNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(TRANSITION_DURATION)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(TRANSITION_DURATION)
                )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(TRANSITION_DURATION))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(TRANSITION_DURATION))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(TRANSITION_DURATION)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(TRANSITION_DURATION)
                )
        },
    ) {

        // ── Splash ──────────────────────────────────────────────────────────
        composable(
            route = Screen.Splash.route,
            enterTransition = { fadeIn(animationSpec = tween(TRANSITION_DURATION)) },
            exitTransition = { fadeOut(animationSpec = tween(TRANSITION_DURATION)) },
        ) {
            val viewModel: SplashViewModel = hiltViewModel()
            SplashScreen(
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToCalibration = {
                    navController.navigate(Screen.Calibration.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToMain = {
                    navController.navigate(Screen.Typing.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                viewModel = viewModel,
            )
        }

        // ── Onboarding ──────────────────────────────────────────────────────
        composable(
            route = Screen.Onboarding.route,
        ) {
            val viewModel: OnboardingViewModel = hiltViewModel()
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Calibration.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                viewModel = viewModel,
            )
        }

        // ── Calibration ─────────────────────────────────────────────────────
        composable(
            route = Screen.Calibration.route,
        ) {
            val viewModel: CalibrationViewModel = hiltViewModel()
            CalibrationScreen(
                viewModel = viewModel,
                onCalibrationComplete = {
                    navController.navigate(Screen.Typing.route) {
                        popUpTo(Screen.Calibration.route) { inclusive = true }
                    }
                },
                onCalibrationSkipped = {
                    navController.navigate(Screen.Typing.route) {
                        popUpTo(Screen.Calibration.route) { inclusive = true }
                    }
                },
            )
        }

        // ── Typing (main screen) ────────────────────────────────────────────
        composable(
            route = Screen.Typing.route,
        ) {
            TypingScreen()
        }

        // ── Pairing ─────────────────────────────────────────────────────────
        composable(
            route = Screen.Pairing.route,
        ) {
            PairingScreenPlaceholder(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── Settings ────────────────────────────────────────────────────────
        composable(
            route = Screen.Settings.route,
        ) {
            SettingsScreenPlaceholder(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDeveloper = {
                    navController.navigate(Screen.Developer.route)
                },
            )
        }

        // ── Developer ───────────────────────────────────────────────────────
        composable(
            route = Screen.Developer.route,
        ) {
            DeveloperScreenPlaceholder(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ── Preview ─────────────────────────────────────────────────────────
        composable(
            route = Screen.Preview.route,
        ) {
            CameraPreviewScreen()
        }
    }
}

@Composable
private fun PairingScreenPlaceholder(
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E27)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = Color(0xFF4A6CF7),
            modifier = Modifier.size(72.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Bluetooth Pairing",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Connect to your laptop or desktop",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White.copy(alpha = 0.6f),
            ),
        )
    }
}

@Composable
private fun SettingsScreenPlaceholder(
    onNavigateBack: () -> Unit,
    onNavigateToDeveloper: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E27)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = Color(0xFF4A6CF7),
            modifier = Modifier.size(72.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Configure VisionBoard preferences",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White.copy(alpha = 0.6f),
            ),
        )
    }
}

@Composable
private fun DeveloperScreenPlaceholder(
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E27)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Code,
            contentDescription = null,
            tint = Color(0xFF4A6CF7),
            modifier = Modifier.size(72.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Developer Options",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Debug tools and diagnostics",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White.copy(alpha = 0.6f),
            ),
        )
    }
}
