package dev.nathanmkaya.kuva.core

import android.view.View
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.ERROR_CAMERA_CLOSED
import androidx.camera.core.ImageCapture.ERROR_CAPTURE_FAILED
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Creates a new Android-specific [Controller] instance.
 *
 * @param config The [Config] to use for the controller.
 * @param previewHost The [PreviewHost] to display the camera preview on.
 * @return A new [Controller] instance.
 */
actual fun createController(config: Config, previewHost: PreviewHost): Controller =
    AndroidController(config, previewHost)

private fun mapAspectRatio(hint: AspectRatioHint): Int? =
    when (hint) {
        AspectRatioHint.DEFAULT -> AspectRatio.RATIO_DEFAULT
        AspectRatioHint.RATIO_4_3 -> AspectRatio.RATIO_4_3
        AspectRatioHint.RATIO_16_9 -> AspectRatio.RATIO_16_9
        AspectRatioHint.SQUARE -> AspectRatio.RATIO_DEFAULT
    }

/** The Android-specific implementation of the [Controller] interface. */
private class AndroidController(private var config: Config, private val previewHost: PreviewHost) :
    Controller {

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var layoutChangeListener: View.OnLayoutChangeListener? = null

    private val _status = MutableStateFlow<CameraStatus>(CameraStatus.Idle)

    override val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1f)

    override val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()
    override val minZoom: Float = 1f

    private val _maxZoom = MutableStateFlow(1f)
    override val maxZoom: StateFlow<Float> = _maxZoom.asStateFlow()

    override suspend fun start() {
        _status.update { CameraStatus.Initializing }
        
        try {
            setupCameraProvider()
            val previewView = setupPreviewView()
            val cameraSelector = createCameraSelector()
            val useCases = createUseCases(previewView)
            bindCamera(previewView, cameraSelector, useCases)
            _status.update { CameraStatus.Running }
        } catch (e: Exception) {
            handleCameraError(e)
        }
    }
    
    private suspend fun setupCameraProvider() {
        if (provider == null) {
            provider = suspendCancellableCoroutine { cont ->
                val future = ProcessCameraProvider.getInstance((previewHost.native as View).context)
                future.addListener(
                    {
                        try {
                            cont.resume(future.get())
                        } catch (e: Exception) {
                            cont.resumeWithException(e)
                        }
                    },
                    ContextCompat.getMainExecutor((previewHost.native as View).context),
                )
            }
        }
    }
    
    private fun setupPreviewView(): PreviewView {
        val previewView = previewHost.native as PreviewView
        layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateRotations() }
        previewView.addOnLayoutChangeListener(layoutChangeListener)
        return previewView
    }
    
    private fun createCameraSelector(): CameraSelector {
        return if (config.lens == Lens.BACK) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }
    
    private fun createUseCases(previewView: PreviewView): Pair<Preview, ImageCapture> {
        val preview = Preview.Builder()
            .apply {
                mapAspectRatio(config.aspectRatio)?.let { setTargetAspectRatio(it) }
                setTargetRotation(previewView.display.rotation)
            }
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
            
        val capture = ImageCapture.Builder()
            .apply {
                mapAspectRatio(config.aspectRatio)?.let { setTargetAspectRatio(it) }
                setTargetRotation(previewView.display.rotation)
                setFlashMode(
                    when (config.flash) {
                        Flash.OFF -> ImageCapture.FLASH_MODE_OFF
                        Flash.ON -> ImageCapture.FLASH_MODE_ON
                        Flash.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    }
                )
            }
            .build()
            .also { imageCapture = it }
            
        return Pair(preview, capture)
    }
    
    private fun bindCamera(previewView: PreviewView, selector: CameraSelector, useCases: Pair<Preview, ImageCapture>) {
        provider?.unbindAll()
        camera = provider?.bindToLifecycle(
            resolveLifecycleOwner(previewView),
            selector,
            useCases.first,
            useCases.second,
        )
        
        // Update zoom capabilities
        camera?.cameraInfo?.zoomState?.value?.let { zoomState ->
            _maxZoom.update { zoomState.maxZoomRatio }
        }
    }
    
    private fun handleCameraError(exception: Exception) {
        _status.update {
            when (exception) {
                is SecurityException ->
                    CameraStatus.Error("Permission denied", Error.PermissionDenied)
                else ->
                    CameraStatus.Error(
                        exception.message ?: "Unknown",
                        Error.Unknown(exception.message.orEmpty(), exception),
                    )
            }
        }
    }

    override suspend fun stop() {
        provider?.unbindAll()
        _status.update { CameraStatus.Idle }
    }

    override fun close() {
        // Stop camera session if running
        provider?.unbindAll()

        // Release references
        camera = null
        preview = null
        imageCapture = null

        // Clear listeners
        val previewView = previewHost.native as PreviewView
        layoutChangeListener?.let { previewView.removeOnLayoutChangeListener(it) }
        layoutChangeListener = null

        // Update final status
        _status.update { CameraStatus.Idle }
    }

    override suspend fun switchLens(): Lens {
        config = config.copy(lens = if (config.lens == Lens.BACK) Lens.FRONT else Lens.BACK)
        start()
        return config.lens
    }

    override suspend fun setFlash(flash: Flash) {
        config = config.copy(flash = flash)
        imageCapture?.flashMode =
            when (flash) {
                Flash.ON -> ImageCapture.FLASH_MODE_ON
                Flash.OFF -> ImageCapture.FLASH_MODE_OFF
                Flash.AUTO -> ImageCapture.FLASH_MODE_AUTO
            }
    }

    override suspend fun setZoom(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
        _zoomRatio.update { ratio }
    }

    override suspend fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    override suspend fun tapToFocus(normX: Float, normY: Float) {
        if (!config.enableTapToFocus) return
        val pv = previewHost.native as PreviewView
        val pt = pv.meteringPointFactory.createPoint(normX * pv.width, normY * pv.height)
        val action =
            FocusMeteringAction.Builder(
                    pt,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                )
                .setAutoCancelDuration(2, TimeUnit.SECONDS)
                .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    override suspend fun capturePhoto(): PhotoResult = suspendCancellableCoroutine { cont ->
        imageCapture?.takePicture(
            ContextCompat.getMainExecutor((previewHost.native as View).context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(
                        when (exception.imageCaptureError) {
                            ERROR_CAMERA_CLOSED -> Error.CameraInUse
                            ERROR_CAPTURE_FAILED ->
                                Error.Hardware(exception.message ?: "Capture failed")

                            else -> Error.Unknown(exception.message ?: "Unknown error")
                        }
                    )
                }

                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        val exif = ExifInterface(bytes.inputStream())
                        val orientation =
                            exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL,
                            )
                        val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, image.width)
                        val height =
                            exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, image.height)

                        cont.resume(
                            PhotoResult(
                                bytes = bytes,
                                width = width,
                                height = height,
                                rotationDegrees = 0,
                                exifOrientationTag = orientation,
                            )
                        )
                    } catch (t: Throwable) {
                        cont.resumeWithException(t)
                    } finally {
                        image.close()
                    }
                }
            },
        )
    }

    private fun updateRotations() {
        val pv = previewHost.native as PreviewView
        val rotation = pv.display.rotation
        imageCapture?.targetRotation = rotation
    }

    private fun resolveLifecycleOwner(view: View): LifecycleOwner {
        return view.findViewTreeLifecycleOwner() ?: error("No lifecycle found")
    }
}
