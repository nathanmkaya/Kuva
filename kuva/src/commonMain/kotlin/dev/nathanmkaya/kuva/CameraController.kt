package dev.nathanmkaya.kuva

import kotlinx.coroutines.flow.StateFlow

/**
 * Main camera controller providing unified access to camera functionality across platforms.
 *
 * This class unifies Android CameraX and iOS AVFoundation behind a single API,
 * providing camera preview, photo capture, and controls (zoom, focus, exposure, torch).
 *
 * ## Usage
 * ```kotlin
 * val controller = CameraController()
 * 
 * // Start preview
 * controller.startPreview(
 *     lensFacing = LensFacing.BACK,
 *     viewport = Viewport(aspectRatio = 4f / 3f)
 * ).onSuccess { capabilities ->
 *     // Camera started successfully
 * }.onFailure { error ->
 *     // Handle camera error
 * }
 * 
 * // Take photo
 * val result = controller.takePhoto()
 * ```
 *
 * ## Lifecycle
 * The controller requires explicit lifecycle management:
 * - Call [startPreview] to begin camera preview
 * - Call [stopPreview] to release camera resources
 * - Always call [stopPreview] when done to prevent resource leaks
 */
expect class CameraController {
    
    /**
     * Configure debug settings for development builds.
     * 
     * @param config Debug configuration. Set enabled=false for production.
     */
    fun setDebugConfig(config: DebugConfig)

    /**
     * Start camera preview with specified configuration.
     *
     * @param lensFacing Camera to use (front or back)
     * @param viewport Preview viewport configuration
     * @param orientationLock Orientation handling mode
     * @return Success with camera capabilities, or failure with error
     */
    suspend fun startPreview(
        lensFacing: LensFacing = LensFacing.BACK,
        viewport: Viewport,
        orientationLock: OrientationLock = OrientationLock.AUTO
    ): Result<CameraCapabilities>

    /**
     * Stop camera preview and release all resources.
     *
     * @return Success or failure result
     */
    suspend fun stopPreview(): Result<Unit>

    /**
     * Capture a photo with specified settings.
     *
     * @param flashMode Flash mode for this capture
     * @param mirrorFrontCamera Whether to mirror front camera captures
     * @param preferHeifIfAvailable Use HEIF format if supported (smaller files on iOS)
     * @param writeToFile Whether to write directly to file (reduces memory usage)
     * @param jpegQuality JPEG quality (1-100), null for default
     * @return Photo capture result with image data or error
     */
    suspend fun takePhoto(
        flashMode: FlashMode = FlashMode.AUTO,
        mirrorFrontCamera: Boolean = false,
        preferHeifIfAvailable: Boolean = false,
        writeToFile: Boolean = false,
        jpegQuality: Int? = null
    ): PhotoResult

    /**
     * Set linear zoom level (normalized 0..1).
     *
     * @param normalized Zoom level from 0.0 (min) to 1.0 (max)
     * @return Success or failure result
     */
    fun setLinearZoom(normalized: Float): Result<Unit>

    /**
     * Set zoom ratio (1.0 = no zoom, higher = more zoom).
     *
     * @param ratio Zoom ratio (must be within device capabilities)
     * @return Success or failure result
     */
    fun setZoomRatio(ratio: Float): Result<Unit>

    /**
     * Enable or disable torch/flashlight.
     *
     * @param enabled Whether torch should be enabled
     * @return Success or failure result
     */
    fun enableTorch(enabled: Boolean): Result<Unit>

    /**
     * Set exposure compensation (EV bias).
     *
     * @param ev Exposure value offset (must be within device range)
     * @return Success or failure result
     */
    fun setExposureEv(ev: Float): Result<Unit>

    /**
     * Set focus point using normalized coordinates.
     *
     * @param nx Normalized X coordinate (0..1)
     * @param ny Normalized Y coordinate (0..1)
     * @return Success or failure result
     */
    fun setFocusPoint(nx: Float, ny: Float): Result<Unit>

    /**
     * Get current camera capabilities.
     *
     * @return Camera capabilities (only valid after successful startPreview)
     */
    fun capabilities(): CameraCapabilities

    /**
     * Get reactive camera state.
     *
     * @return StateFlow of current camera state
     */
    fun state(): CameraState

    /**
     * Convert view coordinates to sensor coordinates.
     *
     * @param x View X coordinate
     * @param y View Y coordinate
     * @return Sensor coordinates as Pair(x, y)
     */
    fun viewToSensor(x: Float, y: Float): Pair<Float, Float>

    /**
     * Convert sensor coordinates to view coordinates.
     *
     * @param x Sensor X coordinate
     * @param y Sensor Y coordinate
     * @return View coordinates as Pair(x, y)
     */
    fun sensorToView(x: Float, y: Float): Pair<Float, Float>

    /**
     * Get debug information stream.
     *
     * @return StateFlow of debug information (empty if debug disabled)
     */
    fun debugInfo(): StateFlow<CameraDebugInfo>
}