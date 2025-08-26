package dev.nathanmkaya.kuva.core

sealed class CameraStatus {
    data object Idle : CameraStatus()

    data object Initializing : CameraStatus()

    data object Running : CameraStatus()

    data class Error(val reason: String, val cause: dev.nathanmkaya.kuva.core.Error? = null) :
        CameraStatus()
}
