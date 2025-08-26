package dev.nathanmkaya.kuva.core

/** Represents the current status of the camera. */
sealed class CameraStatus {
    /** The camera is idle and not actively capturing or initializing. */
    data object Idle : CameraStatus()

    /** The camera is currently initializing. */
    data object Initializing : CameraStatus()

    /** The camera is running and ready to capture. */
    data object Running : CameraStatus()

    /**
     * An error has occurred.
     *
     * @param reason A human-readable description of the error.
     * @param cause The underlying [Error] that caused the status change, if any.
     */
    data class Error(val reason: String, val cause: dev.nathanmkaya.kuva.core.Error? = null) :
        CameraStatus()
}
