package com.visionboard.vision.model

import android.graphics.PointF

data class KeyData(
    val label: String,
    val keyCode: Int,
    val relativePolygon: List<PointF>,
    val neighbors: List<String>
)
