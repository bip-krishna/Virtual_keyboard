package com.visionboard.app.navigation

sealed class Screen(val route: String) {

    data object Splash : Screen("splash")

    data object Onboarding : Screen("onboarding")

    data object Calibration : Screen("calibration")

    data object Typing : Screen("typing")

    data object Pairing : Screen("pairing")

    data object Settings : Screen("settings")

    data object Developer : Screen("developer")

    data object Preview : Screen("preview")
}
