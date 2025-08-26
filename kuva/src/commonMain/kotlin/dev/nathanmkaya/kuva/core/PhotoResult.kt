package dev.nathanmkaya.kuva.core

/**
 * Represents the result of a photo capture.
 *
 * @property bytes The photo data.
 * @property width The width of the photo.
 * @property height The height of the photo.
 * @property rotationDegrees The rotation of the photo in degrees.
 * @property exifOrientationTag The EXIF orientation tag of the photo.
 * @property mimeType The MIME type of the photo. Defaults to "image/jpeg".
 */
data class PhotoResult(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0,
    val exifOrientationTag: Int? = null,
    val mimeType: String = "image/jpeg",
) {
    override fun toString(): String {
        return "PhotoResult(width=$width, height=$height, rotationDegrees=$rotationDegrees, exifOrientationTag=$exifOrientationTag, mimeType=$mimeType"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhotoResult) return false
        return bytes.contentEquals(other.bytes) && width == other.width && height == other.height
    }

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + width + height
}
