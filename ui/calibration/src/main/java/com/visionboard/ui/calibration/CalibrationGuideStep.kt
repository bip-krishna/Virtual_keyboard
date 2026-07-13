package com.visionboard.ui.calibration

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDownwardAlt
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.ui.graphics.vector.ImageVector

sealed class CalibrationGuideStep(
    val index: Int,
    val totalSteps: Int,
    val instruction: String,
    val subtitle: String,
    val icon: ImageVector,
    val angleHint: String,
) {

    data object PlaceFlat : CalibrationGuideStep(
        index = 0,
        totalSteps = 5,
        instruction = "Place keyboard cover flat on a table",
        subtitle = "Make sure it's on a well-lit, flat surface with no obstructions",
        icon = Icons.Filled.Straighten,
        angleHint = "0\u00b0 \u2014 directly overhead",
    )

    data object HoldAbove : CalibrationGuideStep(
        index = 1,
        totalSteps = 5,
        instruction = "Hold phone directly above, about 30 cm away",
        subtitle = "Keep the keyboard centered in the camera frame",
        icon = Icons.Filled.ArrowDownward,
        angleHint = "\u2248 30 cm distance",
    )

    data object TiltLeft : CalibrationGuideStep(
        index = 2,
        totalSteps = 5,
        instruction = "Tilt slightly to the left and capture",
        subtitle = "A gentle 10\u00b3\u201315\u00b0 tilt is enough",
        icon = Icons.Filled.KeyboardArrowLeft,
        angleHint = "\u2248 10\u00b0\u201315\u00b0 left",
    )

    data object TiltRight : CalibrationGuideStep(
        index = 3,
        totalSteps = 5,
        instruction = "Tilt slightly to the right and capture",
        subtitle = "Mirror the previous left tilt angle",
        icon = Icons.Filled.KeyboardArrowRight,
        angleHint = "\u2248 10\u00b0\u201315\u00b0 right",
    )

    data object TiltForward : CalibrationGuideStep(
        index = 4,
        totalSteps = 5,
        instruction = "Tilt slightly forward and capture",
        subtitle = "Tilt the top of the phone toward the keyboard",
        icon = Icons.Filled.ArrowDownwardAlt,
        angleHint = "\u2248 10\u00b0\u201315\u00b0 forward",
    )

    fun next(): CalibrationGuideStep = when (this) {
        is PlaceFlat -> HoldAbove
        is HoldAbove -> TiltLeft
        is TiltLeft -> TiltRight
        is TiltRight -> TiltForward
        is TiltForward -> TiltForward
    }

    val isLastStep: Boolean get() = this is TiltForward
    val stepNumber: Int get() = index + 1
    val displayLabel: String get() = "Step $stepNumber of $totalSteps"

    companion object {
        fun start(): CalibrationGuideStep = PlaceFlat

        val allSteps: List<CalibrationGuideStep> = listOf(
            PlaceFlat,
            HoldAbove,
            TiltLeft,
            TiltRight,
            TiltForward,
        )
    }
}
