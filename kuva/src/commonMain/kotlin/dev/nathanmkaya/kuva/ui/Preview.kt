package dev.nathanmkaya.kuva.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.nathanmkaya.kuva.core.Controller
import dev.nathanmkaya.kuva.core.PreviewHost

/**
 * A composable that displays the camera preview.
 *
 * @param controller The camera controller.
 * @param previewHost The platform-specific view that hosts the camera preview.
 * @param modifier The modifier to apply to the preview.
 */
@Composable
fun Preview(controller: Controller, previewHost: PreviewHost, modifier: Modifier = Modifier) {
    PlatformPreview(previewHost, modifier)
}

/**
 * A platform-specific composable that displays the camera preview.
 *
 * @param host The platform-specific view that hosts the camera preview.
 * @param modifier The modifier to apply to the preview.
 */
@Composable internal expect fun PlatformPreview(host: PreviewHost, modifier: Modifier)
