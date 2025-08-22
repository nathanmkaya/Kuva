# Kuva

Kuva (Finnish: image/picture) is a Kotlin Multiplatform camera library that unifies Android CameraX and iOS AVFoundation behind one clean API. It focuses on preview + photo capture with first-class controls (tap-to-focus, zoom, EV, torch/flash), viewport alignment, normalized coordinates, and a lightweight debug HUD—all designed for Compose MPP and modern app architectures.

**One API. Two platforms. Fully controlled preview and capture.**

---

## ✨ Highlights

- **Unified preview & photo**: Preview + ImageCapture on Android, AVCaptureSession + AVCapturePhotoOutput on iOS.
- **Viewport alignment**: consistent crop/transform between preview and capture.
- **Normalized controls**: coordinates and zoom expressed in 0..1 across platforms.
- **Capability-driven**: query what the device supports; degrade gracefully.
- **Robust errors**: sealed CameraError + Result<T> for every operation.
- **Orientation handling**: explicit OrientationLock + targetRotation / videoOrientation updates.
- **Debug mode**: optional FPS + stall counters, negotiated resolutions, last error—no overhead in release.
- **Compose-friendly**: reactive state via StateFlow, plug-in overlay slot for focus rings, grids, boxes.

---

## 🔧 Modules (proposed)

```
:kuva-core       // common API (expect/actual)
:kuva-android    // CameraX implementation
:kuva-ios        // AVFoundation implementation
:kuva-samples    // sample app with debug HUD, overlays, gestures
```

**Artifacts (placeholder):**

```
group   = io.yourorg.kuva
version = 0.1.0
```

---

## 📦 Installation (Gradle KMP)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google()
  }
}

// build.gradle.kts (root or shared module)
kotlin {
  androidTarget()
  ios() // or iosArm64 + iosSimulatorArm64

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation("io.yourorg.kuva:kuva-core:0.1.0")
      }
    }
    val androidMain by getting {
      dependencies {
        implementation("io.yourorg.kuva:kuva-android:0.1.0")
        implementation("androidx.camera:camera-core:1.3.4")
        implementation("androidx.camera:camera-camera2:1.3.4")
        implementation("androidx.camera:camera-lifecycle:1.3.4")
        implementation("androidx.camera:camera-view:1.3.4")
      }
    }
    val iosMain by getting {
      dependencies {
        implementation("io.yourorg.kuva:kuva-ios:0.1.0")
      }
    }
  }
}
```

iOS integrates via the KMP-produced XCFramework. No Swift wrapper needed—call the Kotlin API from Swift if desired.

---

## 🔒 Permissions

### Android

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.CAMERA" />
<!-- If writing files to public storage pre-SAF -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
```

### iOS (Info.plist)

```xml
<key>NSCameraUsageDescription</key>
<string>We use the camera to take photos.</string>
```

---

## 🧠 Core API (common)

```kotlin
data class EvRange(val minEv: Float, val maxEv: Float, val stepEv: Float?)
data class ZoomRange(val min: Float, val max: Float)
data class Size(val width: Int, val height: Int)

enum class LensFacing { BACK, FRONT }
enum class FlashMode { OFF, ON, AUTO }
enum class OrientationLock { AUTO, PORTRAIT, LANDSCAPE }

data class CameraCapabilities(
    val hasFlash: Boolean,
    val hasTorch: Boolean,
    val supportsTapToFocus: Boolean,
    val supportsExposurePoint: Boolean,
    val supportsExposureCompensation: Boolean,
    val exposureRange: EvRange?,
    val zoomRange: ZoomRange?,
    val supportsLinearZoom: Boolean,
    val minZoom: Float = 1f,
    val supportsManualFocus: Boolean,
    val supportsRawCapture: Boolean = false,
    val supportedEffects: Set<String> = emptySet()
)

data class Viewport(
    val aspectRatio: Float,
    val scaleToFill: Boolean = true,
    val alignCropToAllOutputs: Boolean = true
)

sealed class CameraError {
    class PermissionDenied : CameraError()
    class CameraUnavailable(val msg: String? = null) : CameraError()
    class ConfigurationFailed(val msg: String? = null) : CameraError()
    class CaptureFailed(val msg: String? = null) : CameraError()
    class FileIO(val msg: String? = null) : CameraError()
    class Unsupported(val msg: String? = null) : CameraError()
    class Timeout : CameraError()
    class IllegalState(val msg: String? = null) : CameraError()
    class Unknown(val cause: Throwable? = null) : CameraError()
}

sealed class PhotoResult {
    data class Success(
        val data: ByteArray,
        val width: Int,
        val height: Int,
        val mimeType: String,
        val metadata: Map<String, Any?> = emptyMap()
    ) : PhotoResult()
    data class Error(val error: CameraError) : PhotoResult()
}

interface CameraState {
    val previewSize: kotlinx.coroutines.flow.StateFlow<Size?>
    val zoom: kotlinx.coroutines.flow.StateFlow<Float>           // 0..1
    val exposureBiasEv: kotlinx.coroutines.flow.StateFlow<Float> // EV
    val torchEnabled: kotlinx.coroutines.flow.StateFlow<Boolean>
    val isFocused: kotlinx.coroutines.flow.StateFlow<Boolean?>
}

data class CameraDebugInfo(
    val sessionState: String,              // "idle","configuring","running","stopped","error"
    val activeDevice: String?,
    val actualPreviewResolution: Size?,
    val actualCaptureResolution: Size?,
    val fps: Double,
    val droppedStallCount: Int,
    val lastError: CameraError? = null
)

data class DebugConfig(
    val enabled: Boolean = false,
    val analyzeFps: Boolean = true,
    val targetAnalysisResolution: Size = Size(640, 480),
    val windowSeconds: Int = 3
)

expect class KmpCameraController {
    fun setDebugConfig(config: DebugConfig)

    suspend fun startPreview(
        lensFacing: LensFacing = LensFacing.BACK,
        viewport: Viewport,
        orientationLock: OrientationLock = OrientationLock.AUTO
    ): Result<CameraCapabilities>

    suspend fun stopPreview(): Result<Unit>

    suspend fun takePhoto(
        flashMode: FlashMode = FlashMode.AUTO,
        mirrorFrontCamera: Boolean = false,
        preferHeifIfAvailable: Boolean = false,
        writeToFile: Boolean = false,
        jpegQuality: Int? = null
    ): PhotoResult

    fun setLinearZoom(normalized: Float): Result<Unit> // 0..1
    fun setZoomRatio(ratio: Float): Result<Unit>
    fun enableTorch(enabled: Boolean): Result<Unit>
    fun setExposureEv(ev: Float): Result<Unit>
    fun setFocusPoint(nx: Float, ny: Float): Result<Unit> // 0..1

    fun capabilities(): CameraCapabilities
    fun state(): CameraState

    fun viewToSensor(x: Float, y: Float): Pair<Float, Float>
    fun sensorToView(x: Float, y: Float): Pair<Float, Float>

    fun debugInfo(): kotlinx.coroutines.flow.StateFlow<CameraDebugInfo>
}
```

---

## 🚀 Quick Start (Compose Multiplatform)

```kotlin
@Composable
fun KuvaScreen() {
    val controller = remember { KmpCameraController() }
    val state = controller.state()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        controller.setDebugConfig(DebugConfig(enabled = BuildConfig.DEBUG))
        controller.startPreview(
            lensFacing = LensFacing.BACK,
            viewport   = Viewport(aspectRatio = 4f / 3f),
            orientationLock = OrientationLock.AUTO
        ).onFailure { /* show error */ }
    }

    // Your platform view host + overlay slot:
    KuvaPreview(
        controller = controller,
        modifier = Modifier.fillMaxSize(),
        onTap = { nx, ny -> controller.setFocusPoint(nx, ny) },
        onPinch = { linear -> controller.setLinearZoom(linear) },
        overlay = {
            // draw focus ring, grids, faces, horizon, crop guides...
            // use state.previewSize / controller.viewToSensor() if needed
        }
    )

    // Capture button
    FloatingActionButton(onClick = {
        // consider coroutine scope
        // preferHeifIfAvailable on iOS for smaller size
        // writeToFile=false returns bytes
        // true to file for lower memory spikes
    }) { Text("📸") }
}
```

`KuvaPreview` is a tiny composable that hosts the platform preview view (Android PreviewView, iOS UIView with AVCaptureVideoPreviewLayer) and layers your overlay on top.

---

## 🎯 Gestures & Overlays

### Tap-to-focus
- Send normalized 0..1 coordinates (nx, ny) to `setFocusPoint`.
- Use `controller.viewToSensor()`/`sensorToView()` to keep overlays aligned with the active crop.

### Pinch-to-zoom
- Map pinch delta to a normalized linear zoom [0..1] and call `setLinearZoom`.
- Read back `state.zoom` to drive your slider.

### Focus ring example (pseudo)

```kotlin
overlay = {
  val focused by controller.state().isFocused.collectAsState()
  val lastTap by remember { mutableStateOf<Offset?>(null) } // update on tap
  lastTap?.let { tap ->
    Box(Modifier.offset { IntOffset(tap.x.toInt(), tap.y.toInt()) }
       .size(80.dp)
       .border(2.dp, if (focused == true) Color.Green else Color.White, CircleShape))
  }
}
```

---

## 🔁 Orientation

- **Android**: Kuva keeps Preview and ImageCapture targetRotation in sync.
- **iOS**: Kuva updates both previewLayer.connection?.videoOrientation and the photo connection's videoOrientation.
- Use `OrientationLock.AUTO` to follow device changes, or lock to portrait/landscape.

---

## 🧯 Errors & Troubleshooting

Common failures map to `CameraError`:
- `PermissionDenied` — missing/denied camera permission.
- `CameraUnavailable` — hardware busy/disconnected.
- `ConfigurationFailed` — bind/config errors.
- `CaptureFailed` — photo pipeline error.
- `FileIO` — disk write failures.
- `Unsupported` — feature not available on device.
- `IllegalState`, `Timeout`, `Unknown`.

### Debugging
- Enable `DebugConfig(enabled = true)` in dev only.
- Subscribe to `debugInfo()` and optionally show a HUD:
  - session state, device ID, negotiated preview/capture sizes
  - rolling FPS and stall count
  - last error snapshot

---

## 🧪 Test Plan (suggested)

- **Devices**: at least 3 Android OEMs (Pixel/Samsung/OnePlus) + 2 iOS devices.
- **Orientations**: rotate during preview and during capture.
- **Mapping**: golden tests for tap-to-focus ring alignment in each orientation.
- **Memory**: burst 20 captures; monitor peak RSS and GC/alloc churn.
- **Permissions**: denied → grant in Settings → resume flows.

---

## 🗺️ Roadmap

- Video capture (with consistent controls)
- Image analysis (YUV/planes) in common API
- RAW/Pro features where available
- Advanced effects (HDR/Night/etc.) via capability flags
- In-preview face, barcode, and composition guides (opt-in analyzers)

---

## 🤝 Contributing

Issues and PRs welcome! Please:
1. Open an issue describing the use case/bug.
2. Include device models, OS versions, and logs.
3. Add tests where possible (unit + sample scenarios).

---

## 📄 License

MIT (proposed). See LICENSE.

---

## 🙏 Acknowledgments

- Android CameraX team for a pragmatic camera API.
- Apple AVFoundation for powerful, low-level control.
- Community projects that inspired the overlay/viewport ergonomics.

---

## Why "Kuva"?

Short, friendly, memorable—and a real word for image/picture. Exactly what this library is about.
