package dev.nathanmkaya.kuva.core

import androidx.camera.view.PreviewView

/**
 * The Android-specific implementation of [PreviewHost].
 *
 * @param platformContext The Android-specific [PlatformContext].
 */
actual class PreviewHost actual constructor(private val platformContext: PlatformContext) {
    /** The native [PreviewView] that hosts the camera preview. */
    actual val native: Any by lazy { PreviewView(platformContext.context) }
}
