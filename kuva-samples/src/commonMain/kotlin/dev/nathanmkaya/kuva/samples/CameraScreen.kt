package dev.nathanmkaya.kuva.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.nathanmkaya.kuva.core.AspectRatioHint
import dev.nathanmkaya.kuva.core.CameraStatus
import dev.nathanmkaya.kuva.core.Config
import dev.nathanmkaya.kuva.core.Flash
import dev.nathanmkaya.kuva.core.Lens
import dev.nathanmkaya.kuva.core.PhotoResult
import dev.nathanmkaya.kuva.core.PreviewHost
import dev.nathanmkaya.kuva.core.bindTo
import dev.nathanmkaya.kuva.core.createController
import dev.nathanmkaya.kuva.core.rememberPlatformContext
import dev.nathanmkaya.kuva.ui.Preview
import kotlinx.coroutines.launch

/**
 * Cross‑platform Camera Screen for a shared UI KMP module.
 *
 * Responsibilities:
 * - Handles permission gating via [hasPermission]/[onRequestPermission]
 * - Renders preview and minimal controls (switch lens, torch, capture, zoom)
 * - Exposes capture results via [onResult]
 */
@Composable
fun CameraScreen(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    // Optional config override. If null, a sensible default is used.
    config: Config? = null,
    onResult: (PhotoResult) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Resolve a config: enable analysis pipeline automatically if plugins are provided
    val effectiveConfig =
        remember(config) {
            config
                ?: Config(
                    lens = Lens.BACK,
                    flash = Flash.OFF,
                    aspectRatio = AspectRatioHint.RATIO_16_9,
                    enableTapToFocus = true,
                )
        }

    val platformCtx = rememberPlatformContext()
    val host = remember { PreviewHost(platformCtx) }
    val controller = remember { createController(effectiveConfig, host) }

    // Bind controller to lifecycle
    val scope = rememberCoroutineScope()
    DisposableEffect(controller, lifecycleOwner.lifecycle) {
        val binding = controller.bindTo(lifecycleOwner.lifecycle, scope)
        onDispose { binding.close() }
    }

    // Observe status & zoom
    val status by controller.status.collectAsState()
    val zoom by controller.zoomRatio.collectAsState(initial = 1f)
    val maxZoom by controller.maxZoom.collectAsState(initial = 1f)

    Surface(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Header / status row
            StatusBar(
                status = status,
                hasPermission = hasPermission,
                onRequestPermission = onRequestPermission,
            )

            if (!hasPermission) {
                PermissionGate(onRequestPermission)
            } else {
                // Preview + HUD
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Preview(
                        controller = controller,
                        previewHost = host,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Gesture overlay for tap-to-focus and pinch-to-zoom
                    Box(
                        Modifier.matchParentSize()
                            .pointerInput(Unit) {
                                detectTapGestures { pos ->
                                    val size = this.size
                                    val nx = (pos.x / size.width).coerceIn(0f, 1f)
                                    val ny = (pos.y / size.height).coerceIn(0f, 1f)
                                    scope.launch { controller.tapToFocus(nx, ny) }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoomChange, _ ->
                                    if (zoomChange != 1f) {
                                        val currentZoom = zoom
                                        val newZoom =
                                            (currentZoom * zoomChange).coerceIn(
                                                controller.minZoom,
                                                maxZoom,
                                            )
                                        scope.launch { controller.setZoom(newZoom) }
                                    }
                                }
                            }
                    )

                    // Controls at the bottom
                    ControlsBar(
                        zoom = zoom,
                        maxZoom = maxZoom,
                        onSwitchLens = { scope.launch { controller.switchLens() } },
                        onToggleTorch = { enabled ->
                            scope.launch { controller.setTorch(enabled) }
                        },
                        onZoomChange = { z -> scope.launch { controller.setZoom(z) } },
                        onCapture = {
                            scope.launch {
                                runCatching { controller.capturePhoto() }.onSuccess(onResult)
                            }
                        },
                        modifier =
                            Modifier.align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBar(
    status: CameraStatus,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val text =
            when {
                !hasPermission -> "Permission required"
                status is CameraStatus.Initializing -> "Starting camera…"
                status is CameraStatus.Running -> "Camera ready"
                status is CameraStatus.Error -> "Error: ${'$'}{status.reason}"
                else -> "Idle"
            }
        Text(text)
        Spacer(Modifier.weight(1f))
        if (!hasPermission) Button(onClick = onRequestPermission) { Text("Grant") }
    }
}

@Composable
private fun PermissionGate(onRequestPermission: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera permission is needed.")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRequestPermission) { Text("Grant Permission") }
    }
}

@Composable
private fun ControlsBar(
    zoom: Float,
    maxZoom: Float,
    onSwitchLens: () -> Unit,
    onToggleTorch: (Boolean) -> Unit,
    onZoomChange: (Float) -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var torch by remember { mutableStateOf(false) }

    Column(modifier.padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onSwitchLens) { Text("Switch") }
            Button(
                onClick = {
                    torch = !torch
                    onToggleTorch(torch)
                }
            ) {
                Text(if (torch) "Torch OFF" else "Torch ON")
            }
            Button(onClick = onCapture) { Text("Capture") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Zoom", modifier = Modifier.width(48.dp))
            Slider(
                value = zoom.coerceIn(1f, if (maxZoom.isFinite() && maxZoom >= 1f) maxZoom else 1f),
                onValueChange = onZoomChange,
                valueRange = 1f..(if (maxZoom.isFinite() && maxZoom >= 1f) maxZoom else 1f),
            )
        }
    }
}
