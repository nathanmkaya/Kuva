package dev.nathanmkaya.kuva.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleOwner
import dev.nathanmkaya.kuva.core.Config
import dev.nathanmkaya.kuva.core.Controller
import dev.nathanmkaya.kuva.core.PreviewHost
import dev.nathanmkaya.kuva.core.bindTo
import dev.nathanmkaya.kuva.core.createController
import dev.nathanmkaya.kuva.core.rememberPlatformContext

@Composable
fun Camera(
    config: Config,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier,
    onControllerReady: (Controller) -> Unit,
) {
    val context = rememberPlatformContext()
    val host = remember { PreviewHost(context) }
    val controller = remember(config, host) { createController(config, host) }
    val scope = rememberCoroutineScope()

    DisposableEffect(controller, lifecycleOwner.lifecycle) {
        val binding = controller.bindTo(lifecycleOwner.lifecycle, scope)
        onDispose { binding.close() }
    }

    LaunchedEffect(Unit) { onControllerReady(controller) }

    Preview(controller, host, modifier)
}
