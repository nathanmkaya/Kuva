package dev.nathanmkaya.kuva.core

sealed class Error(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    data object PermissionDenied : Error("Camera permission denied")

    data object CameraInUse : Error("Camera device is in use by another app")

    class Hardware(msg: String, cause: Throwable? = null) : Error(msg, cause)

    class Unknown(msg: String, cause: Throwable? = null) : Error(msg, cause)
}
