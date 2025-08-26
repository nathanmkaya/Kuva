package dev.nathanmkaya.kuva.ui

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.nathanmkaya.kuva.core.PreviewHost

@Composable
internal actual fun PlatformPreview(host: PreviewHost, modifier: Modifier) {
    AndroidView(factory = { host.native as PreviewView }, modifier = modifier)
}
