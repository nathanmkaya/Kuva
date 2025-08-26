package dev.nathanmkaya.kuva.core

import platform.UIKit.UIView

actual class PreviewHost actual constructor(platformContext: PlatformContext) {
    actual val native: Any
        get() = UIView()
}
