package dev.nathanmkaya.kuva.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import dev.nathanmkaya.kuva.core.PreviewHost
import platform.UIKit.UIView

@Composable
internal actual fun PlatformPreview(host: PreviewHost, modifier: Modifier) {
    UIKitView(modifier = modifier, factory = { host.native as UIView })
}
