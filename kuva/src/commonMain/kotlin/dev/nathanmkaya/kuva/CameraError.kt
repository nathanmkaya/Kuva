package dev.nathanmkaya.kuva

/**
 * Sealed hierarchy of camera-related errors.
 * 
 * All camera operations return either successful results or these typed errors,
 * allowing for explicit error handling and recovery strategies.
 */
sealed class CameraError: Exception() {
    
    /**
     * Camera permission was denied by the user.
     * 
     * Recovery: Request permission again or direct user to settings.
     */
    data object PermissionDenied : CameraError()
    
    /**
     * Camera hardware is unavailable (busy, disconnected, or not present).
     * 
     * @param msg Optional detailed error message
     */
    data class CameraUnavailable(val msg: String? = null) : CameraError()
    
    /**
     * Camera configuration failed (invalid settings, unsupported combination).
     * 
     * @param msg Optional detailed error message
     */
    data class ConfigurationFailed(val msg: String? = null) : CameraError()
    
    /**
     * Photo/video capture failed.
     * 
     * @param msg Optional detailed error message
     */
    data class CaptureFailed(val msg: String? = null) : CameraError()
    
    /**
     * File I/O operation failed (disk full, permission, etc.).
     * 
     * @param msg Optional detailed error message
     */
    data class FileIO(val msg: String? = null) : CameraError()
    
    /**
     * Requested feature/operation is not supported on this device.
     * 
     * @param msg Optional detailed error message
     */
    data class Unsupported(val msg: String? = null) : CameraError()
    
    /**
     * Operation timeout (focus, capture, etc.).
     */
    data object Timeout : CameraError()
    
    /**
     * Invalid state for the requested operation.
     * 
     * @param msg Optional detailed error message
     */
    data class IllegalState(val msg: String? = null) : CameraError()
    
    /**
     * Unknown/unexpected error occurred.
     * 
     * @param cause Original exception if available
     */
    data class Unknown(override val cause: Throwable? = null) : CameraError()
}

/**
 * Result of a photo capture operation.
 */
sealed class PhotoResult {
    /**
     * Photo capture succeeded.
     * 
     * @param data Image data as byte array
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param mimeType Image MIME type (e.g., "image/jpeg", "image/heif")
     * @param metadata Additional metadata (EXIF, etc.)
     */
    data class Success(
        val data: ByteArray,
        val width: Int,
        val height: Int,
        val mimeType: String,
        val metadata: Map<String, Any?> = emptyMap()
    ) : PhotoResult() {
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            
            if (!data.contentEquals(other.data)) return false
            if (width != other.width) return false
            if (height != other.height) return false
            if (mimeType != other.mimeType) return false
            if (metadata != other.metadata) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + metadata.hashCode()
            return result
        }
    }
    
    /**
     * Photo capture failed.
     * 
     * @param error The specific camera error that occurred
     */
    data class Error(val error: CameraError) : PhotoResult()
}

/**
 * Extension function to convert exceptions to camera errors.
 */
internal fun Throwable.toCameraError(): CameraError = when (this) {
    is IllegalStateException -> CameraError.IllegalState(message)
    is IllegalArgumentException -> CameraError.ConfigurationFailed(message)
    else -> CameraError.Unknown(this)
}