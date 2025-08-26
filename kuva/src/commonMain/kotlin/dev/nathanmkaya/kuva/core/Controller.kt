package dev.nathanmkaya.kuva.core

import kotlinx.coroutines.flow.StateFlow

interface Controller {
    val status: StateFlow<CameraStatus>

    val zoomRatio: StateFlow<Float>
    val minZoom: Float
    val maxZoom: Float

    suspend fun start()

    suspend fun stop()

    /**
     * Terminal cleanup of all camera resources. After calling close(), the controller cannot be
     * restarted. This method is idempotent and safe to call multiple times.
     */
    fun close()

    suspend fun switchLens(): Lens

    suspend fun setFlash(flash: Flash)

    suspend fun setZoom(ratio: Float)

    suspend fun setTorch(enabled: Boolean)

    suspend fun tapToFocus(normX: Float, normY: Float)

    suspend fun capturePhoto(): PhotoResult
}

expect fun createController(config: Config, previewHost: PreviewHost): Controller
