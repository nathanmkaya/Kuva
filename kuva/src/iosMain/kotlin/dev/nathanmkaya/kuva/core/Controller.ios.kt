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
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
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
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
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
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.exposureMode
import platform.AVFoundation.exposurePointOfInterest
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.focusMode
import platform.AVFoundation.focusPointOfInterest
import platform.AVFoundation.hasTorch
import platform.AVFoundation.isExposurePointOfInterestSupported
import platform.AVFoundation.isFocusPointOfInterestSupported
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.torchMode
import platform.AVFoundation.videoZoomFactor
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSThread
import platform.Foundation.getBytes
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIDeviceOrientationDidChangeNotification
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

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
    private var orientationObserver: Any? = null

    private val _status = MutableStateFlow<CameraStatus>(CameraStatus.Idle)
    override val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    override val zoomRatio: StateFlow<Float> = _zoom.asStateFlow()

    override val minZoom: Float = 1f

    private val _maxZoom = MutableStateFlow(1f)
    override val maxZoom: StateFlow<Float> = _maxZoom.asStateFlow()

    override suspend fun start() {
        _status.update { CameraStatus.Initializing }

        if (!ensureAuthorized()) {
            _status.update { CameraStatus.Error("Permission denied") }
            return
        }

        configureSession()
        setupPreviewLayer()
        startSession()
        updateZoomBounds()
        _status.update { CameraStatus.Running }
    }

    private fun configureSession() {
        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetPhoto

        setupCameraDevice()
        clearExistingInputsOutputs()
        addCameraInput()
        addPhotoOutput()

        session.commitConfiguration()
    }

    private fun setupCameraDevice() {
        val position =
            if (config.lens == Lens.BACK) {
                AVCaptureDevicePositionBack
            } else {
                AVCaptureDevicePositionFront
            }

        val discovery =
            AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
                deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
                mediaType = AVMediaTypeVideo,
                position = position,
            )

        device = (discovery.devices.firstOrNull() as? AVCaptureDevice) ?: error("No camera")
    }

    private fun clearExistingInputsOutputs() {
        // Remove existing IO (iterate over snapshots!)
        session.inputs.toList().forEach { session.removeInput(it as AVCaptureInput) }
        session.outputs.toList().forEach { session.removeOutput(it as AVCaptureOutput) }
    }

    private fun addCameraInput() {
        val input =
            AVCaptureDeviceInput.deviceInputWithDevice(device!!, error = null)
                ?: error("Failed to create camera input")
        require(session.canAddInput(input)) { "Cannot add camera input to session" }
        session.addInput(input)
    }

    private fun addPhotoOutput() {
        val photoOutput = AVCapturePhotoOutput()
        require(session.canAddOutput(photoOutput)) { "Cannot add photo output to session" }
        session.addOutput(photoOutput)
        this.photoOutput = photoOutput
    }

    private fun setupPreviewLayer() {
        onMain {
            val container = previewHost.native as PreviewContainerView
            if (previewLayer.superlayer == null) {
                previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
                container.layer.addSublayer(previewLayer)
                container.videoLayer = previewLayer // container keeps layer sized in layoutSubviews
            }
            applyPreviewOrientation()
        }
        beginOrientationUpdates() // keep preview orientation fresh
    }

    private fun startSession() {
        // Start session off main to avoid jank
        dispatch_async(dispatch_get_global_queue(0, 0u)) { session.startRunning() }
    }

    private fun updateZoomBounds() {
        device?.activeFormat?.let { fmt -> _maxZoom.update { fmt.videoMaxZoomFactor.toFloat() } }
    }

    override suspend fun stop() {
        session.stopRunning()
        _status.update { CameraStatus.Idle }
        // Keep the preview layer attached; only remove on close()
    }

    override fun close() {
        // Stop session if running
        if (session.running) session.stopRunning()

        // Remove orientation observer
        orientationObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        orientationObserver = null

        // Remove preview layer on main
        onMain { previewLayer.removeFromSuperlayer() }

        // Release refs
        device = null
        photoOutput = null

        _status.update { CameraStatus.Idle }
    }

    override suspend fun switchLens(): Lens {
        config = config.copy(lens = if (config.lens == Lens.BACK) Lens.FRONT else Lens.BACK)
        start()
        return config.lens
    }

    override suspend fun setFlash(flash: Flash) {
        config = config.copy(flash = flash)
    }

    override suspend fun setZoom(ratio: Float) {
        device?.let { dev ->
            dev.lockForConfiguration(null)
            val clamped = ratio.toDouble().coerceIn(1.0, dev.activeFormat.videoMaxZoomFactor)
            dev.videoZoomFactor = clamped
            dev.unlockForConfiguration()
            _zoom.value = clamped.toFloat()
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
                            return
                        }
                        val data =
                            didFinishProcessingPhoto.fileDataRepresentation()
                                ?: run {
                                    cont.cancel(CancellationException("Failed to get photo data"))
                                    return
                                }
                        val bytes = ByteArray(data.length.toInt())
                        bytes.usePinned { pinned ->
                            data.getBytes(pinned.addressOf(0), length = data.length)
                        }

                        val pb = didFinishProcessingPhoto.pixelBuffer
                        val width = pb?.let { CVPixelBufferGetWidth(it).toInt() } ?: 0
                        val height = pb?.let { CVPixelBufferGetHeight(it).toInt() } ?: 0

                        // Most consumers honor EXIF; keep rotationDegrees=0 for parity with Android
                        // EXIF path
                        cont.resume(
                            PhotoResult(
                                bytes = bytes,
                                width = width,
                                height = height,
                                rotationDegrees = 0,
                                exifOrientationTag = null,
                            )
                        )
                    }
                },
        )
    }

    // --- Orientation & threading helpers ---

    fun currentVideoOrientation(): AVCaptureVideoOrientation {
        val orientation = UIDevice.currentDevice.orientation
        return when (orientation) {
            UIDeviceOrientation.UIDeviceOrientationPortrait -> AVCaptureVideoOrientationPortrait
            UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown ->
                AVCaptureVideoOrientationPortraitUpsideDown
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft ->
                AVCaptureVideoOrientationLandscapeRight
            UIDeviceOrientation.UIDeviceOrientationLandscapeRight ->
                AVCaptureVideoOrientationLandscapeLeft
            else -> AVCaptureVideoOrientationPortrait
        }
    }

    private fun applyPreviewOrientation() {
        onMain {
            previewLayer.connection?.let { conn ->
                if (conn.isVideoOrientationSupported()) {
                    conn.videoOrientation = currentVideoOrientation()
                }
                // Optional mirroring for front camera (preview only)
                conn.automaticallyAdjustsVideoMirroring = false
                conn.videoMirrored = (config.lens == Lens.FRONT)
            }
        }
    }

    private fun beginOrientationUpdates() {
        if (orientationObserver != null) return // already observing
        UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()
        orientationObserver =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIDeviceOrientationDidChangeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                applyPreviewOrientation()
            }
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (NSThread.isMainThread) block()
        else dispatch_async(dispatch_get_main_queue()) { block() }
    }

    private suspend fun ensureAuthorized(): Boolean = suspendCancellableCoroutine { cont ->
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> cont.resume(true)
            AVAuthorizationStatusDenied,
            AVAuthorizationStatusRestricted -> cont.resume(false)
            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    cont.resume(granted)
                }
            }
            else -> cont.resume(false)
        }
    }
}
