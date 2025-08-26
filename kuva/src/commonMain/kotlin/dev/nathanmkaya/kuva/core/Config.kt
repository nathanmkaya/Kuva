package dev.nathanmkaya.kuva.core

data class Config(
    val lens: Lens = Lens.BACK,
    val flash: Flash = Flash.OFF,
    val aspectRatio: AspectRatioHint = AspectRatioHint.DEFAULT,
    val enableTapToFocus: Boolean = true,
    val enforceCaptureAspectRatio: Boolean = false,
)

enum class Lens {
    BACK,
    FRONT,
}

enum class Flash {
    AUTO,
    ON,
    OFF,
}

enum class AspectRatioHint {
    DEFAULT,
    RATIO_4_3,
    RATIO_16_9,
    SQUARE,
}
