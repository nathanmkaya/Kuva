@file:Suppress("MissingPermission")
package dev.nathanmkaya.kuva

import android.content.Context
import android.util.Rational
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.io.path.createTempFile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android implementation of CameraController using CameraX.
 */
actual class CameraController {

    // ---------- Public Android-only hooks (UI must call before startPreview) ----------
    fun setPreviewView(previewView: PreviewView) { this.previewView = previewView }
    fun setLifecycleOwner(owner: LifecycleOwner) { this.lifecycleOwner = owner }

    // ---------- Internals ----------
    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null

    private var orientationListener: OrientationEventListener? = null

    private val _previewSize = MutableStateFlow<Size?>(null)
    private val _zoom = MutableStateFlow(0f)
    private val _ev = MutableStateFlow(0f)
    private val _torch = MutableStateFlow(false)
    private val _focused: MutableStateFlow<Boolean?> = MutableStateFlow(null)

    private val _debug = MutableStateFlow(
        CameraDebugInfo(
            sessionState = "idle",
            activeDevice = null,
            actualPreviewResolution = null,
            actualCaptureResolution = null,
            fps = 0.0,
            droppedStallCount = 0,
            lastError = null
        )
    )

    private var debugConfig: DebugConfig = DebugConfig()

    private var caps: CameraCapabilities = CameraCapabilities(
        hasFlash = false,
        hasTorch = false,
        supportsTapToFocus = true,
        supportsExposurePoint = true,
        supportsExposureCompensation = false,
        exposureRange = null,
        zoomRange = null,
        supportsLinearZoom = true,
        minZoom = 1f,
        supportsManualFocus = true,
        supportsRawCapture = false,
        supportedEffects = emptySet()
    )

    actual fun setDebugConfig(config: DebugConfig) { 
        debugConfig = config 
    }
    
    actual fun debugInfo(): StateFlow<CameraDebugInfo> = _debug

    actual fun capabilities(): CameraCapabilities = caps

    actual fun state(): CameraState = object : CameraState {
        override val previewSize: StateFlow<Size?> = _previewSize
        override val zoom: StateFlow<Float> = _zoom
        override val exposureBiasEv: StateFlow<Float> = _ev
        override val torchEnabled: StateFlow<Boolean> = _torch
        override val isFocused: StateFlow<Boolean?> = _focused
    }

    // View<->sensor helpers (normalized coordinates). On Android, MeteringPoint gives normalized 0..1.
    actual fun viewToSensor(x: Float, y: Float): Pair<Float, Float> {
        val pv = previewView ?: return x to y
        val pt = pv.meteringPointFactory.createPoint(x, y)
        return pt.x to pt.y
    }

    // Inverse mapping is approximate without full transform; use simple scaling as a fallback.
    actual fun sensorToView(x: Float, y: Float): Pair<Float, Float> {
        val pv = previewView ?: return x to y
        return (x * pv.width) to (y * pv.height)
    }

    actual suspend fun startPreview(
        lensFacing: LensFacing,
        viewport: Viewport,
        orientationLock: OrientationLock
    ): Result<CameraCapabilities> = withContext(Dispatchers.Main) {
        val pv = previewView ?: return@withContext Result.failure(IllegalStateException("PreviewView not set. Call setPreviewView()."))
        val owner = lifecycleOwner ?: return@withContext Result.failure(IllegalStateException("LifecycleOwner not set. Call setLifecycleOwner()."))

        _debug.value = _debug.value.copy(sessionState = "configuring")

        val ctx = pv.context.applicationContext
        val provider = runCatching {
            cameraProvider ?: ctx.getCameraProvider().also { cameraProvider = it }
        }.getOrElse { t ->
            _debug.value = _debug.value.copy(sessionState = "error", lastError = CameraError.CameraUnavailable(t.message))
            return@withContext Result.failure(t)
        }

        // Unbind previous
        provider.unbindAll()

        val selector = when (lensFacing) {
            LensFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            LensFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        }

        val displayRotation = pv.display?.rotation ?: Surface.ROTATION_0

        // Build preview
        val previewUseCase = Preview.Builder()
            .setTargetRotation(displayRotation)
            .build().also { preview = it }

        // Build image capture
        val imageCaptureUseCase = ImageCapture.Builder()
            .setTargetRotation(displayRotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build().also { imageCapture = it }

        // ViewPort for consistent crop across use cases
        val aspect = viewport.aspectRatio
        val rational = Rational((aspect * 1000).toInt(), 1000)
        val vp = ViewPort.Builder(rational, displayRotation)
            .setScaleType(if (viewport.scaleToFill) ViewPort.FILL_CENTER else ViewPort.FIT)
            .build()

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(previewUseCase)
            .addUseCase(imageCaptureUseCase)
            .setViewPort(vp)
            .build()

        runCatching {
            // Bind
            val cam = provider.bindToLifecycle(owner, selector, useCaseGroup)
            camera = cam
            previewUseCase.surfaceProvider = pv.surfaceProvider

            // Observe zoom/torch to update state flows
            cam.cameraInfo.zoomState.observe(owner, Observer { z ->
                z?.let { _zoom.value = it.linearZoom }
            })
            cam.cameraInfo.torchState.observe(owner, Observer { t ->
                _torch.value = (t == TorchState.ON)
            })

            // Compute capabilities
            val hasFlash = cam.cameraInfo.hasFlashUnit()
            val es = cam.cameraInfo.exposureState
            val supportsEv = es.isExposureCompensationSupported
            val evRange = if (supportsEv) {
                val r = es.exposureCompensationRange
                val step = es.exposureCompensationStep.toFloat()
                EvRange(r.lower * step, r.upper * step, step)
            } else null
            val zState = cam.cameraInfo.zoomState.value
            val zRange = zState?.let { ZoomRange(it.minZoomRatio, it.maxZoomRatio) }

            caps = CameraCapabilities(
                hasFlash = hasFlash,
                hasTorch = true,
                supportsTapToFocus = true,
                supportsExposurePoint = true,
                supportsExposureCompensation = supportsEv,
                exposureRange = evRange,
                zoomRange = zRange,
                supportsLinearZoom = true,
                minZoom = zRange?.min ?: 1f,
                supportsManualFocus = true,
                supportsRawCapture = false,
                supportedEffects = emptySet()
            )

            // Publish negotiated sizes (ResolutionInfo is public API)
            _previewSize.value = previewUseCase.resolutionInfo?.resolution?.let { Size(it.width, it.height) }
            val capRes = imageCaptureUseCase.resolutionInfo?.resolution
            _debug.value = _debug.value.copy(
                sessionState = "running",
                activeDevice = selector.lensFacing?.toString(),
                actualPreviewResolution = _previewSize.value,
                actualCaptureResolution = capRes?.let { Size(it.width, it.height) }
            )

            // Orientation updates: keep targetRotation in sync
            orientationListener?.disable()
            orientationListener = object : OrientationEventListener(pv.context) {
                override fun onOrientationChanged(orientation: Int) {
                    val rot = pv.display?.rotation ?: return
                    if (rot != this@CameraController.imageCapture?.targetRotation || rot != this@CameraController.preview?.attachedSurfaceResolutionRotation()) {
                        this@CameraController.preview?.targetRotation = rot
                        this@CameraController.imageCapture?.targetRotation = rot
                    }
                }
            }.apply { enable() }

            caps
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { t ->
                _debug.value = _debug.value.copy(sessionState = "error", lastError = CameraError.ConfigurationFailed(t.message))
                Result.failure(t)
            }
        )
    }

    actual suspend fun stopPreview(): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            orientationListener?.disable()
            orientationListener = null
            cameraProvider?.unbindAll()
            camera = null
            preview = null
            imageCapture = null
            _debug.value = _debug.value.copy(sessionState = "stopped")
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { t ->
                _debug.value = _debug.value.copy(sessionState = "error", lastError = CameraError.IllegalState(t.message))
                Result.failure(t)
            }
        )
    }

    actual suspend fun takePhoto(
        flashMode: FlashMode,
        mirrorFrontCamera: Boolean,
        preferHeifIfAvailable: Boolean,
        writeToFile: Boolean,
        jpegQuality: Int?
    ): PhotoResult = withContext(Dispatchers.Main) {
        val ic = imageCapture ?: return@withContext PhotoResult.Error(CameraError.IllegalState("ImageCapture not initialized"))
        val pv = previewView ?: return@withContext PhotoResult.Error(CameraError.IllegalState("PreviewView missing"))

        // Map flash
        ic.flashMode = when (flashMode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }

        runCatching {
            if (writeToFile) {
                // File-based approach when explicitly requested
                val ext = if (preferHeifIfAvailable) ".heif" else ".jpg"
                val outputFile = createTempFile(
                    directory = pv.context.cacheDir.toPath(),
                    prefix = "kuva_",
                    suffix = ext
                ).toFile()
                
                val opts = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                val meta = suspendOutput(opts, ic, ContextCompat.getMainExecutor(pv.context))
                
                val bytes = withContext(Dispatchers.IO) { outputFile.readBytes() }
                
                PhotoResult.Success(
                    data = bytes,
                    width = meta.width,
                    height = meta.height,
                    mimeType = if (preferHeifIfAvailable) "image/heif" else "image/jpeg",
                    metadata = mapOf("path" to outputFile.absolutePath)
                )
            } else {
                // In-memory approach (default) - eliminates file management issues
                val outputStream = ByteArrayOutputStream()
                val opts = ImageCapture.OutputFileOptions.Builder(outputStream).build()

                val meta = suspendOutput(opts, ic, ContextCompat.getMainExecutor(pv.context))
                
                val bytes = outputStream.toByteArray()
                
                // Add size validation to prevent OOM
                val maxSizeBytes = 50 * 1024 * 1024 // 50MB limit
                if (bytes.size > maxSizeBytes) {
                    return@withContext PhotoResult.Error(
                        CameraError.FileIO("Photo too large: ${bytes.size} bytes (max: $maxSizeBytes)")
                    )
                }

                PhotoResult.Success(
                    data = bytes,
                    width = meta.width,
                    height = meta.height,
                    mimeType = if (preferHeifIfAvailable) "image/heif" else "image/jpeg",
                    metadata = emptyMap() // No file path since we're using in-memory approach
                )
            }
        }.getOrElse { t ->
            _debug.value = _debug.value.copy(lastError = CameraError.CaptureFailed(t.message))
            PhotoResult.Error(CameraError.CaptureFailed(t.message))
        }
    }

    actual fun setLinearZoom(normalized: Float): Result<Unit> =
        runCatching { camera?.cameraControl?.setLinearZoom(normalized.coerceIn(0f, 1f)) }

    actual fun setZoomRatio(ratio: Float): Result<Unit> =
        runCatching { camera?.cameraControl?.setZoomRatio(ratio.coerceAtLeast(1f)) }

    actual fun enableTorch(enabled: Boolean): Result<Unit> =
        runCatching {
            _torch.value = enabled
            camera?.cameraControl?.enableTorch(enabled)
        }

    actual fun setExposureEv(ev: Float): Result<Unit> = runCatching {
        val cam = camera ?: return@runCatching
        val es = cam.cameraInfo.exposureState
        if (!es.isExposureCompensationSupported) return@runCatching
        val step = es.exposureCompensationStep.toFloat()
        val idx = (ev / step).toInt().coerceIn(es.exposureCompensationRange.lower, es.exposureCompensationRange.upper)
        cam.cameraControl.setExposureCompensationIndex(idx)
        _ev.value = idx * step
    }

    actual fun setFocusPoint(nx: Float, ny: Float): Result<Unit> = runCatching {
        val pv = previewView ?: return@runCatching
        val cam = camera ?: return@runCatching
        val xPx = (nx.coerceIn(0f, 1f) * pv.width)
        val yPx = (ny.coerceIn(0f, 1f) * pv.height)
        val point = pv.meteringPointFactory.createPoint(xPx, yPx)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val future = cam.cameraControl.startFocusAndMetering(action)
        val exec: Executor = ContextCompat.getMainExecutor(pv.context)
        future.addListener({
            runCatching {
                _focused.value = future.get().isFocusSuccessful
            }.onFailure { _focused.value = null }
        }, exec)
    }

    // ---------- Helpers ----------

    private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
        withContext(Dispatchers.Main) {
            suspendCancellable { cont ->
                val f = ProcessCameraProvider.getInstance(this@getCameraProvider)
                f.addListener({ cont.resume(f.get()) }, ContextCompat.getMainExecutor(this@getCameraProvider))
            }
        }

    private suspend fun <T> suspendCancellable(block: (CancellableContinuation<T>) -> Unit): T =
        withContext(Dispatchers.Main) { kotlinx.coroutines.suspendCancellableCoroutine(block) }

    private suspend fun suspendOutput(
        opts: ImageCapture.OutputFileOptions,
        ic: ImageCapture,
        executor: Executor
    ): OutputMeta = withContext(Dispatchers.Main) {
        suspendCancellable { cont ->
            ic.takePicture(opts, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val res = ic.resolutionInfo?.resolution
                    cont.resume(OutputMeta(res?.width ?: 0, res?.height ?: 0))
                }
                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            })
        }
    }

    private data class OutputMeta(val width: Int, val height: Int)
}

// Small extension to check preview rotation without reflection; this returns -1 on older APIs, so we ignore it.
private fun Preview.attachedSurfaceResolutionRotation(): Int = 
    runCatching { this.targetRotation }.getOrElse { -1 }