package dev.nathanmkaya.kuva.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.nathanmkaya.kuva.CameraController

/**
 * Platform-specific camera preview host.
 * 
 * This composable provides a camera preview surface that integrates
 * with the native camera implementation on each platform:
 * - Android: Uses CameraX PreviewView
 * - iOS: Uses AVCaptureVideoPreviewLayer (when implemented)
 * 
 * The preview surface must be configured before calling startPreview()
 * on the controller.
 */
@Composable
expect fun PlatformPreviewHost(
    controller: CameraController,
    modifier: Modifier = Modifier
)