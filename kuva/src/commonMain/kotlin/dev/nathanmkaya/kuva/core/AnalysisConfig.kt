package dev.nathanmkaya.kuva.core

/**
 * Strategy for handling backpressure in the camera analysis pipeline. Backpressure occurs when the
 * analysis process cannot keep up with the rate of incoming frames.
 */
enum class Backpressure {
    /**
     * Keep only the latest frame and drop older frames. This is useful for real-time applications
     * where latency is critical.
     */
    KEEP_LATEST,

    /**
     * Block the producer until the consumer is ready. This ensures that no frames are dropped, but
     * may introduce latency.
     */
    BLOCK_PRODUCER,
}

/**
 * Configuration for the camera analysis pipeline. These settings are applied when analysis is
 * started and cannot be changed dynamically. To apply new settings, you must call
 * `restartAnalysis`.
 *
 * @property backpressure The backpressure strategy to use. Defaults to [Backpressure.KEEP_LATEST].
 * @property targetFps A hint for the desired frames per second. The actual FPS may vary.
 * @property reuseBuffers Whether to reuse image buffers. This can improve performance by reducing
 *   memory allocations. Defaults to `true`.
 * @property enabled Whether the analysis pipeline is enabled. Defaults to `false`.
 */
data class AnalysisConfig(
    val backpressure: Backpressure = Backpressure.KEEP_LATEST,
    val targetFps: Int? = null, // hint only
    val reuseBuffers: Boolean = true,
    val enabled: Boolean = false,
)
