package dev.nathanmkaya.kuva.core

import kotlinx.coroutines.flow.StateFlow

/** The main interface for controlling the camera. */
interface Controller {
    /** The current status of the camera. */
    val status: StateFlow<CameraStatus>

    /** The current zoom ratio of the camera. */
    val zoomRatio: StateFlow<Float>

    /** The minimum zoom ratio supported by the camera. */
    val minZoom: Float

    /** The maximum zoom ratio supported by the camera. */
    val maxZoom: StateFlow<Float>

    /** Starts the camera preview. */
    suspend fun start()

    /** Stops the camera preview. */
    suspend fun stop()

    /**
     * Terminal cleanup of all camera resources. After calling close(), the controller cannot be
     * restarted. This method is idempotent and safe to call multiple times.
     */
    fun close()

    /**
     * Switches between the front and back camera lenses.
     *
     * @return The new [Lens] that was switched to.
     */
    suspend fun switchLens(): Lens

    /**
     * Sets the flash mode.
     *
     * @param flash The [Flash] mode to set.
     */
    suspend fun setFlash(flash: Flash)

    /**
     * Sets the zoom ratio.
     *
     * @param ratio The zoom ratio to set. Must be between [minZoom] and [maxZoom].
     */
    suspend fun setZoom(ratio: Float)

    /**
     * Enables or disables the torch.
     *
     * @param enabled Whether to enable or disable the torch.
     */
    suspend fun setTorch(enabled: Boolean)

    /**
     * Sets the focus point.
     *
     * @param normX The normalized x-coordinate of the focus point.
     * @param normY The normalized y-coordinate of the focus point.
     */
    suspend fun tapToFocus(normX: Float, normY: Float)

    /**
     * Captures a photo.
     *
     * @return A [PhotoResult] containing the captured photo data.
     */
    suspend fun capturePhoto(): PhotoResult
}

/**
 * Creates a new [Controller] instance.
 *
 * @param config The [Config] to use for the controller.
 * @param previewHost The [PreviewHost] to display the camera preview on.
 * @return A new [Controller] instance.
 */
expect fun createController(config: Config, previewHost: PreviewHost): Controller
