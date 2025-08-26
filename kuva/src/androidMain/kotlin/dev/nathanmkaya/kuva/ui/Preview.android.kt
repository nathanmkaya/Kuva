package dev.nathanmkaya.kuva.ui

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.nathanmkaya.kuva.core.PreviewHost

/**
 * The Android-specific implementation of [PlatformPreview].
 *
 * @param host The [PreviewHost] that contains the native [PreviewView].
 * @param modifier The modifier to apply to the preview.
 */
@Composable
internal actual fun PlatformPreview(host: PreviewHost, modifier: Modifier) {
    AndroidView(factory = { host.native as PreviewView }, modifier = modifier)
}
