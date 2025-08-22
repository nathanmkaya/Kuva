package dev.nathanmkaya.kuva

import kotlinx.coroutines.flow.StateFlow

/**
 * Represents a 2D size with width and height.
 */
data class Size(
    val width: Int,
    val height: Int
) {
    /**
     * Calculate aspect ratio (width / height).
     */
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

/**
 * Exposure value range supported by the camera.
 */
data class EvRange(
    val minEv: Float,
    val maxEv: Float,
    val stepEv: Float?
)

/**
 * Zoom range supported by the camera.
 */
data class ZoomRange(
    val min: Float,
    val max: Float
)

/**
 * Camera lens facing direction.
 */
enum class LensFacing {
    /** Rear-facing camera */
    BACK,
    /** Front-facing camera */
    FRONT
}

/**
 * Flash mode for photo capture.
 */
enum class FlashMode {
    /** Flash disabled */
    OFF,
    /** Flash always on */
    ON,
    /** Automatic flash based on lighting conditions */
    AUTO
}

/**
 * Orientation lock modes.
 */
enum class OrientationLock {
    /** Follow device orientation automatically */
    AUTO,
    /** Lock to portrait orientation */
    PORTRAIT,
    /** Lock to landscape orientation */
    LANDSCAPE
}

/**
 * Camera capabilities available on the current device.
 */
data class CameraCapabilities(
    /** Whether flash is available */
    val hasFlash: Boolean,
    /** Whether torch/flashlight is available */
    val hasTorch: Boolean,
    /** Whether tap-to-focus is supported */
    val supportsTapToFocus: Boolean,
    /** Whether tap-to-expose is supported */
    val supportsExposurePoint: Boolean,
    /** Whether exposure compensation is supported */
    val supportsExposureCompensation: Boolean,
    /** Exposure compensation range (null if not supported) */
    val exposureRange: EvRange?,
    /** Zoom range (null if zoom not supported) */
    val zoomRange: ZoomRange?,
    /** Whether linear zoom is supported */
    val supportsLinearZoom: Boolean,
    /** Minimum zoom ratio */
    val minZoom: Float = 1f,
    /** Whether manual focus is supported */
    val supportsManualFocus: Boolean,
    /** Whether RAW capture is supported */
    val supportsRawCapture: Boolean = false,
    /** Set of supported camera effects/extensions */
    val supportedEffects: Set<String> = emptySet()
)

/**
 * Viewport configuration for camera preview and capture alignment.
 */
data class Viewport(
    /** Target aspect ratio for preview/capture */
    val aspectRatio: Float,
    /** Whether to scale to fill the viewport */
    val scaleToFill: Boolean = true,
    /** Whether to align crop across all outputs */
    val alignCropToAllOutputs: Boolean = true
)

/**
 * Reactive camera state that can be observed for UI updates.
 */
interface CameraState {
    /** Current preview size (null if not started) */
    val previewSize: StateFlow<Size?>
    /** Current zoom level (0..1 normalized) */
    val zoom: StateFlow<Float>
    /** Current exposure bias in EV */
    val exposureBiasEv: StateFlow<Float>
    /** Whether torch is currently enabled */
    val torchEnabled: StateFlow<Boolean>
    /** Focus state (true=focused, false=not focused, null=unknown) */
    val isFocused: StateFlow<Boolean?>
}

/**
 * Debug configuration for development builds.
 */
data class DebugConfig(
    /** Whether debug mode is enabled */
    val enabled: Boolean = false,
    /** Whether to analyze FPS performance */
    val analyzeFps: Boolean = true,
    /** Target resolution for analysis (lower = better performance) */
    val targetAnalysisResolution: Size = Size(640, 480),
    /** Rolling window for FPS calculation (seconds) */
    val windowSeconds: Int = 3
)

/**
 * Debug information for development and troubleshooting.
 */
data class CameraDebugInfo(
    /** Current session state */
    val sessionState: String,
    /** Active camera device identifier */
    val activeDevice: String?,
    /** Actual preview resolution being used */
    val actualPreviewResolution: Size?,
    /** Actual capture resolution being used */
    val actualCaptureResolution: Size?,
    /** Current frames per second */
    val fps: Double,
    /** Number of dropped/stalled frames */
    val droppedStallCount: Int,
    /** Last error encountered (null if none) */
    val lastError: CameraError? = null
)