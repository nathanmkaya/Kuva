package dev.nathanmkaya.kuva.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import dev.nathanmkaya.kuva.core.PreviewContainerView
import dev.nathanmkaya.kuva.core.PreviewHost
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

/**
 * The iOS-specific implementation of [PlatformPreview].
 *
 * @param host The [PreviewHost] that contains the native [UIView].
 * @param modifier The modifier to apply to the preview.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformPreview(host: PreviewHost, modifier: Modifier) {
    UIKitView(
        modifier = modifier,
        factory = { host.native as UIView },
        update = { v -> (v as PreviewContainerView).videoLayer?.frame = v.bounds },
    )
}
