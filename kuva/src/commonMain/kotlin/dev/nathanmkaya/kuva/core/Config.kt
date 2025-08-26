package dev.nathanmkaya.kuva.core

/**
 * Configuration for the camera.
 *
 * @property lens The camera lens to use. Defaults to [Lens.BACK].
 * @property flash The flash mode to use. Defaults to [Flash.OFF].
 * @property aspectRatio The aspect ratio to hint for the camera preview. Defaults to
 *   [AspectRatioHint.DEFAULT].
 * @property enableTapToFocus Whether to enable tap-to-focus. Defaults to `true`.
 * @property enforceCaptureAspectRatio Whether to enforce the aspect ratio of the captured photo to
 *   match the preview. Defaults to `false`.
 */
data class Config(
    val lens: Lens = Lens.BACK,
    val flash: Flash = Flash.OFF,
    val aspectRatio: AspectRatioHint = AspectRatioHint.DEFAULT,
    val enableTapToFocus: Boolean = true,
    val enforceCaptureAspectRatio: Boolean = false,
)

/** The camera lens to use. */
enum class Lens {
    /** The back-facing camera. */
    BACK,

    /** The front-facing camera. */
    FRONT,
}

/** The flash mode to use. */
enum class Flash {
    /** The flash will be used automatically when needed. */
    AUTO,

    /** The flash will always be on. */
    ON,

    /** The flash will always be off. */
    OFF,
}

/** A hint for the aspect ratio of the camera preview. */
enum class AspectRatioHint {
    /** The default aspect ratio of the device. */
    DEFAULT,

    /** A 4:3 aspect ratio. */
    RATIO_4_3,

    /** A 16:9 aspect ratio. */
    RATIO_16_9,

    /** A square aspect ratio. */
    SQUARE,
}
