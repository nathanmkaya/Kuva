package dev.nathanmkaya.kuva.core

import platform.UIKit.UIView

/**
 * The iOS-specific implementation of [PreviewHost].
 *
 * @param platformContext The iOS-specific [PlatformContext].
 */
actual class PreviewHost actual constructor(platformContext: PlatformContext) {
    /** The native [UIView] that hosts the camera preview. */
    actual val native: Any
        get() = UIView()
}
