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

actual fun createController(config: Config, previewHost: PreviewHost): Controller =
    AndroidController(config, previewHost)

private fun mapAspectRatio(hint: AspectRatioHint): Int? =
    when (hint) {
        AspectRatioHint.DEFAULT -> AspectRatio.RATIO_DEFAULT
        AspectRatioHint.RATIO_4_3 -> AspectRatio.RATIO_4_3
        AspectRatioHint.RATIO_16_9 -> AspectRatio.RATIO_16_9
        AspectRatioHint.SQUARE -> AspectRatio.RATIO_DEFAULT
    }

private class AndroidController(private var config: Config, private val previewHost: PreviewHost) :
    Controller {

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null

    private val _status = MutableStateFlow<CameraStatus>(CameraStatus.Idle)

    override val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1f)

    override val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()
    override var minZoom: Float = 1f
        private set

    override var maxZoom: Float = 1f
        private set

    override suspend fun start() {
        _status.update { CameraStatus.Initializing }

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

        val pv = previewHost.native as PreviewView

        pv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateRotations() }

        val selector =
            if (config.lens == Lens.BACK) CameraSelector.DEFAULT_BACK_CAMERA
            else CameraSelector.DEFAULT_FRONT_CAMERA

        val preview =
            Preview.Builder()
                .apply {
                    mapAspectRatio(config.aspectRatio)?.let { setTargetAspectRatio(it) }
                    setTargetRotation(pv.display.rotation)
                }
                .build()
                .also { it.surfaceProvider = pv.surfaceProvider }

        val capture =
            ImageCapture.Builder()
                .apply {
                    mapAspectRatio(config.aspectRatio)?.let { setTargetAspectRatio(it) }
                    setTargetRotation(pv.display.rotation)
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

        val useCases = mutableListOf(preview, capture)

        try {
            provider?.unbindAll()
            camera =
                provider?.bindToLifecycle(
                    resolveLifecycleOwner(pv),
                    selector,
                    *useCases.toTypedArray(),
                )
            camera?.cameraInfo?.zoomState?.value?.let { zs ->
                minZoom = zs.minZoomRatio
                maxZoom = zs.maxZoomRatio
            }
            _status.update { CameraStatus.Running }
        } catch (e: Exception) {
            _status.update {
                when (e) {
                    is SecurityException ->
                        CameraStatus.Error("Permission denied", Error.PermissionDenied)

                    else ->
                        CameraStatus.Error(
                            e.message ?: "Unknown",
                            Error.Unknown(e.message.orEmpty(), e),
                        )
                }
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
        val pv = previewHost.native as PreviewView
        // TODO: pv.removeOnLayoutChangeListener({})

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
