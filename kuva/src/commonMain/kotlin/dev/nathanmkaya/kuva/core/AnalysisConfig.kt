package dev.nathanmkaya.kuva.core

enum class Backpressure {
    KEEP_LATEST,
    BLOCK_PRODUCER,
}

/** Pipeline knobs set at start; change requires restartAnalysis. */
data class AnalysisConfig(
    val backpressure: Backpressure = Backpressure.KEEP_LATEST,
    val targetFps: Int? = null, // hint only
    val reuseBuffers: Boolean = true,
    val enabled: Boolean = false,
)
