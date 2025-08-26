package dev.nathanmkaya.kuva.core

import androidx.camera.view.PreviewView

actual class PreviewHost actual constructor(private val platformContext: PlatformContext) {
    actual val native: Any by lazy { PreviewView(platformContext.context) }
}
