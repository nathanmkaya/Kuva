package dev.nathanmkaya.kuva.core

/** Represents an error that can occur in the camera. */
sealed class Error(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    /** The camera permission was denied. */
    data object PermissionDenied : Error("Camera permission denied")

    /** The camera device is in use by another app. */
    data object CameraInUse : Error("Camera device is in use by another app")

    /**
     * A hardware error occurred.
     *
     * @param msg A human-readable description of the error.
     * @param cause The underlying [Throwable] that caused the error, if any.
     */
    class Hardware(msg: String, cause: Throwable? = null) : Error(msg, cause)

    /**
     * An unknown error occurred.
     *
     * @param msg A human-readable description of the error.
     * @param cause The underlying [Throwable] that caused the error, if any.
     */
    class Unknown(msg: String, cause: Throwable? = null) : Error(msg, cause)
}
