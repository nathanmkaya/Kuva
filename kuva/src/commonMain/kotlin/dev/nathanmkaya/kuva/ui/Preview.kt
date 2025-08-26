package dev.nathanmkaya.kuva.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.nathanmkaya.kuva.core.Controller
import dev.nathanmkaya.kuva.core.PreviewHost

@Composable
fun Preview(controller: Controller, previewHost: PreviewHost, modifier: Modifier = Modifier) {
    PlatformPreview(previewHost, modifier)
}

@Composable internal expect fun PlatformPreview(host: PreviewHost, modifier: Modifier)
