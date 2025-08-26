@file:OptIn(ExperimentalForeignApi::class)

package dev.nathanmkaya.kuva.core

import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureExposureModeContinuousAutoExposure
import platform.AVFoundation.AVCaptureFlashModeAuto
import platform.AVFoundation.AVCaptureFlashModeOff
import platform.AVFoundation.AVCaptureFlashModeOn
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.exposureMode
import platform.AVFoundation.exposurePointOfInterest
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.focusMode
import platform.AVFoundation.focusPointOfInterest
import platform.AVFoundation.hasTorch
import platform.AVFoundation.isExposurePointOfInterestSupported
import platform.AVFoundation.isFocusPointOfInterestSupported
import platform.AVFoundation.torchMode
import platform.AVFoundation.videoZoomFactor
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.getBytes
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIView
import platform.darwin.NSObject

/**
 * Creates a new iOS-specific [Controller] instance.
 *
 * @param config The [Config] to use for the controller.
 * @param previewHost The [PreviewHost] to display the camera preview on.
 * @return A new [Controller] instance.
 */
actual fun createController(config: Config, previewHost: PreviewHost): Controller =
    IosController(config, previewHost)

/** The iOS-specific implementation of the [Controller] interface. */
private class IosController(private var config: Config, private val previewHost: PreviewHost) :
    Controller {

    private val session = AVCaptureSession()
    private var device: AVCaptureDevice? = null
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session)
    private var photoOutput: AVCapturePhotoOutput? = null

    private val _status = MutableStateFlow<CameraStatus>(CameraStatus.Idle)
    override val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    override val zoomRatio: StateFlow<Float> = _zoom.asStateFlow()
    override var minZoom: Float = 1f
        private set

    override var maxZoom: Float = device?.activeFormat?.videoMaxZoomFactor?.toFloat() ?: 1f
        private set

    override suspend fun start() {
        _status.update { CameraStatus.Initializing }
        session.beginConfiguration()

        // Select the camera device
        val position =
            if (config.lens == Lens.BACK) AVCaptureDevicePositionBack
            else AVCaptureDevicePositionFront
        val discovery =
            AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
                deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
                mediaType = AVMediaTypeVideo,
                position = position,
            )
        device = (discovery.devices.firstOrNull() as? AVCaptureDevice) ?: error("No camera")

        // Add the device input to the session
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device!!, error = null)
        input?.let { if (session.canAddInput(input)) session.addInput(input) }

        // Add the photo output to the session
        val po = AVCapturePhotoOutput()
        if (session.canAddOutput(po)) session.addOutput(po)
        photoOutput = po

        // Configure the preview layer
        previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
        previewLayer.setSession(session)
        if (previewLayer.superlayer == null) {
            (previewHost.native as UIView).layer.addSublayer(previewLayer)
        }
        previewLayer.frame = (previewHost.native as UIView).bounds
        applyPreviewOrientation()

        // Start the session
        session.commitConfiguration()
        session.startRunning()
        _status.update { CameraStatus.Running }

        // Get the zoom bounds
        device?.activeFormat?.let { fmt ->
            minZoom = 1f
            maxZoom = (device?.activeFormat?.videoMaxZoomFactor ?: 1.0).toFloat()
        }
    }

    override suspend fun stop() {
        session.stopRunning()
        _status.update { CameraStatus.Idle }
    }

    override fun close() {
        // Stop session if running
        if (session.isRunning()) {
            session.stopRunning()
        }

        // Remove preview layer from superlayer
        previewLayer.removeFromSuperlayer()

        // Release references
        device = null
        photoOutput = null

        // Update final status
        _status.update { CameraStatus.Idle }
    }

    override suspend fun switchLens(): Lens {
        config = config.copy(lens = if (config.lens == Lens.BACK) Lens.FRONT else Lens.BACK)
        stop()
        start()
        return config.lens
    }

    override suspend fun setFlash(flash: Flash) {
        config = config.copy(flash = flash)
    }

    override suspend fun setZoom(ratio: Float) {
        device?.let { dev ->
            dev.lockForConfiguration(null)
            dev.videoZoomFactor = ratio.toDouble().coerceAtMost(dev.activeFormat.videoMaxZoomFactor)
            dev.unlockForConfiguration()
            _zoom.value = ratio
        }
    }

    override suspend fun setTorch(enabled: Boolean) {
        device?.let { dev ->
            if (dev.hasTorch) {
                dev.lockForConfiguration(null)
                dev.torchMode = if (enabled) AVCaptureTorchModeOn else AVCaptureTorchModeOff
                dev.unlockForConfiguration()
            }
        }
    }

    override suspend fun tapToFocus(normX: Float, normY: Float) {
        if (!config.enableTapToFocus) return

        val view = previewHost.native as UIView
        val bounds = view.bounds
        val layerPoint =
            CGPointMake(
                x = normX.toDouble() * CGRectGetWidth(bounds),
                y = normY.toDouble() * CGRectGetHeight(bounds),
            )
        val devicePoint = previewLayer.captureDevicePointOfInterestForPoint(layerPoint)

        device?.let { dev ->
            dev.lockForConfiguration(null)
            if (dev.isFocusPointOfInterestSupported()) {
                dev.focusPointOfInterest = devicePoint
                dev.focusMode = AVCaptureFocusModeContinuousAutoFocus
            }
            if (dev.isExposurePointOfInterestSupported()) {
                dev.exposurePointOfInterest = devicePoint
                dev.exposureMode = AVCaptureExposureModeContinuousAutoExposure
            }
            dev.unlockForConfiguration()
        }
    }

    override suspend fun capturePhoto(): PhotoResult = suspendCancellableCoroutine { cont ->
        val settings =
            AVCapturePhotoSettings.photoSettings().apply {
                flashMode =
                    when (config.flash) {
                        Flash.AUTO -> AVCaptureFlashModeAuto
                        Flash.ON -> AVCaptureFlashModeOn
                        Flash.OFF -> AVCaptureFlashModeOff
                    }
            }
        photoOutput?.capturePhotoWithSettings(
            settings,
            delegate =
                object : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
                    override fun captureOutput(
                        output: AVCapturePhotoOutput,
                        didFinishProcessingPhoto: AVCapturePhoto,
                        error: NSError?,
                    ) {
                        if (error != null) {
                            cont.cancel(CancellationException(error.localizedDescription))
                        } else {
                            val data = didFinishProcessingPhoto.fileDataRepresentation()
                            if (data != null) {
                                val byteArray = ByteArray(data.length.toInt())
                                byteArray.usePinned { pinned ->
                                    data.getBytes(pinned.addressOf(0), length = data.length)
                                }

                                // Get dimensions from the photo
                                val pixelBuffer = didFinishProcessingPhoto.pixelBuffer
                                val width =
                                    pixelBuffer?.let { CVPixelBufferGetWidth(it).toInt() } ?: 0
                                val height =
                                    pixelBuffer?.let { CVPixelBufferGetHeight(it).toInt() } ?: 0

                                // Get rotation from metadata
                                val metadata = didFinishProcessingPhoto.metadata
                                val orientation = metadata["Orientation"] as? NSNumber
                                val rotationDegrees =
                                    when (orientation?.intValue) {
                                        3 -> 180
                                        6 -> 90
                                        8 -> 270
                                        else -> 0
                                    }

                                cont.resume(
                                    PhotoResult(
                                        bytes = byteArray,
                                        width = width,
                                        height = height,
                                        rotationDegrees = rotationDegrees,
                                        exifOrientationTag = null,
                                    )
                                )
                            } else {
                                cont.cancel(CancellationException("Failed to get photo data"))
                            }
                        }
                    }
                },
        )
    }

    private fun avOrientationForDevice(): AVCaptureVideoOrientation {
        // Simple mapping; consider using windowScene.interfaceOrientation if available.
        return when (UIDevice.currentDevice.orientation) {
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft ->
                AVCaptureVideoOrientationLandscapeRight
            UIDeviceOrientation.UIDeviceOrientationLandscapeRight ->
                AVCaptureVideoOrientationLandscapeLeft
            UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown ->
                AVCaptureVideoOrientationPortraitUpsideDown
            else -> AVCaptureVideoOrientationPortrait
        }
    }

    private fun applyPreviewOrientation() {
        previewLayer.connection?.let { conn ->
            if (conn.isVideoOrientationSupported()) {
                conn.videoOrientation = avOrientationForDevice()
            }
        }
    }
}
