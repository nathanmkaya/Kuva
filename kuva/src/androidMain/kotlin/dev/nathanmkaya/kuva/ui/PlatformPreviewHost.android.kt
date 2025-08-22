package dev.nathanmkaya.kuva.ui

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.nathanmkaya.kuva.CameraController

/**
 * Android implementation of PlatformPreviewHost using CameraX PreviewView.
 * 
 * This composable creates an AndroidView that wraps CameraX's PreviewView
 * and automatically configures it with the provided CameraController.
 */
@Composable
actual fun PlatformPreviewHost(
    controller: CameraController,
    modifier: Modifier
) {
    val lifecycle = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                controller.setPreviewView(this)
                controller.setLifecycleOwner(lifecycle)
            }
        },
        update = { /* No-op; controller manages binding via startPreview() */ }
    )
}