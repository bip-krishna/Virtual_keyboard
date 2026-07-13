package com.visionboard.camera.service

import android.os.Binder
import com.visionboard.camera.processor.FrameProcessingResult
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class CameraState {
    IDLE,
    OPENING,
    PREVIEWING,
    CAPTURING,
    ERROR
}

class CameraBinder internal constructor(
    private val service: CameraServiceImpl
) : Binder() {

    val frames: SharedFlow<FrameProcessingResult>
        get() = service.frames

    val cameraState: StateFlow<CameraState>
        get() = service.cameraState

    val currentZoom: StateFlow<Float>
        get() = service.currentZoom

    val isTorchEnabled: StateFlow<Boolean>
        get() = service.isTorchEnabled

    fun startPreview() {
        service.startPreview()
    }

    fun stopPreview() {
        service.stopPreview()
    }

    fun captureStillImage(onCaptured: (ByteArray) -> Unit) {
        service.captureStillImage(onCaptured)
    }

    fun setZoom(zoomRatio: Float) {
        service.setZoom(zoomRatio)
    }

    fun zoomIn() {
        service.zoomIn()
    }

    fun zoomOut() {
        service.zoomOut()
    }

    fun setTorch(enabled: Boolean) {
        service.setTorch(enabled)
    }

    fun toggleTorch() {
        service.toggleTorch()
    }

    fun destroy() {
        service.destroy()
    }
}

internal interface CameraServiceImpl {
    val frames: SharedFlow<FrameProcessingResult>
    val cameraState: StateFlow<CameraState>
    val currentZoom: StateFlow<Float>
    val isTorchEnabled: StateFlow<Boolean>
    fun startPreview()
    fun stopPreview()
    fun captureStillImage(onCaptured: (ByteArray) -> Unit)
    fun setZoom(zoomRatio: Float)
    fun zoomIn()
    fun zoomOut()
    fun setTorch(enabled: Boolean)
    fun toggleTorch()
    fun destroy()
}
