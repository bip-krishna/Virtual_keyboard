package com.visionboard.camera.config

import androidx.camera.core.CameraSelector

enum class LensFacing {
    BACK,
    FRONT;

    internal fun toCameraSelector(): CameraSelector = when (this) {
        BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
    }
}

enum class CameraResolution(val width: Int, val height: Int) {
    SD_480P(640, 480),
    HD_720P(1280, 720),
    FHD_1080P(1920, 1080),
    UHD_4K(3840, 2160);

    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

data class CameraConfig(
    val resolution: CameraResolution = CameraResolution.HD_720P,
    val fpsTarget: Int = 30,
    val useHighSpeed: Boolean = false,
    val enableAutoFocus: Boolean = true,
    val lensFacing: LensFacing = LensFacing.BACK
) {
    companion object {
        val DEFAULT = CameraConfig()
        val HIGH_PERFORMANCE = CameraConfig(
            resolution = CameraResolution.FHD_1080P,
            fpsTarget = 60,
            useHighSpeed = true
        )
        val LOW_LATENCY = CameraConfig(
            resolution = CameraResolution.SD_480P,
            fpsTarget = 30,
            useHighSpeed = false,
            enableAutoFocus = false
        )
        val REAR_DEFAULT = CameraConfig(lensFacing = LensFacing.BACK)
        val FRONT_DEFAULT = CameraConfig(lensFacing = LensFacing.FRONT)
    }
}
