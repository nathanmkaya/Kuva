package dev.nathanmkaya.kuva.core

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView

/**
 * The iOS-specific implementation of [PreviewHost].
 *
 * @param platformContext The iOS-specific [PlatformContext].
 */
actual class PreviewHost actual constructor(platformContext: PlatformContext) {
    /** The native [UIView] that hosts the camera preview. */
    internal val view: PreviewContainerView = PreviewContainerView()
    actual val native: Any = view
}

@OptIn(ExperimentalForeignApi::class)
class PreviewContainerView : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var videoLayer: AVCaptureVideoPreviewLayer? = null

    override fun layoutSubviews() {
        super.layoutSubviews()
        // keep the layer perfectly sized even as UIKitView changes
        videoLayer?.frame = this.bounds
    }

    override fun didMoveToWindow() {
        super.didMoveToWindow()
        // ensure correct size the moment we get attached to a window
        videoLayer?.frame = this.bounds
    }
}
