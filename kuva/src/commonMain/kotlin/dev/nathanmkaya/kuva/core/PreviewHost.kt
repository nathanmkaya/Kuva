package dev.nathanmkaya.kuva.core

/**
 * A platform-specific view that hosts the camera preview.
 *
 * @param platformContext The platform-specific context.
 */
expect class PreviewHost(platformContext: PlatformContext) {
    /** The native view that hosts the camera preview. */
    val native: Any
}
