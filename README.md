# Kuva

**Minimal, Cross‑Platform Camera for Kotlin Multiplatform**  
*Preview · Controller · Config*

Kuva (Finnish: _image/picture_) is a lean Kotlin Multiplatform camera library that provides just the essentials for modern app camera integration. No complex state machines or domain abstractions—just a clean, predictable API that works identically on Android and iOS.

## Why Kuva?

- **🎯 Minimal Scope**: Focus on photo capture, preview, and basic controls
- **🔄 Cross-Platform**: Identical API for Android CameraX and iOS AVFoundation  
- **📱 Compose-First**: Natural integration with Compose Multiplatform
- **⚡ Essential Features**: Everything you need, nothing you don't
- **🔧 Easy Setup**: Simple configuration with sensible defaults

## Features

- **Photo Capture**: JPEG with automatic EXIF orientation handling
- **Live Preview**: Cross-platform camera preview with tap-to-focus
- **Camera Controls**: Lens switching, flash/torch, zoom with native limits
- **Aspect Ratios**: 4:3, 16:9, Square aspect ratio hints
- **Frame Analysis**: Optional YUV420 plugin system for QR/barcode scanning
- **Lifecycle Binding**: Automatic start/stop with AndroidX Lifecycle KMP

## Quick Start

### 1. Basic Setup

```kotlin
@Composable
fun CameraScreen(lifecycleOwner: LifecycleOwner) {
    val context = rememberPlatformContext()
    val host = remember { KuvaPreviewHost(context) }
    val controller = remember { 
        createKuvaController(
            config = KuvaConfig(
                lens = Lens.Back,
                aspectRatio = AspectRatioHint.Ratio16_9,
                enableTapToFocus = true
            ),
            previewHost = host
        )
    }
    
    val scope = rememberCoroutineScope()
    
    // Automatic lifecycle binding
    DisposableEffect(controller, lifecycleOwner.lifecycle) {
        val binding = controller.bindTo(lifecycleOwner.lifecycle, scope)
        onDispose { binding.close() }
    }
    
    Box(Modifier.fillMaxSize()) {
        KuvaPreview(controller, host, Modifier.fillMaxSize())
        
        // Tap-to-focus overlay
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val normalizedX = offset.x / size.width
                        val normalizedY = offset.y / size.height
                        scope.launch {
                            controller.tapToFocus(normalizedX, normalizedY)
                        }
                    }
                }
        )
    }
}
```

### 2. Photo Capture

```kotlin
// Simple capture
val result = controller.capturePhoto()
println("Captured ${result.bytes.size} bytes, ${result.width}x${result.height}")

// Access EXIF orientation (Android) or embedded orientation (iOS)
val orientationTag = result.exifOrientationTag // Android: ExifInterface constant, iOS: null
val rotationDegrees = result.rotationDegrees   // Usually 0 (orientation in EXIF)
```

### 3. Camera Controls

```kotlin
// Lens switching
val newLens = controller.switchLens() // Returns Front or Back

// Flash modes
controller.setFlash(Flash.Auto)
controller.setTorch(enabled = true)

// Zoom control with bounds
val minZoom = controller.minZoom // e.g., 1.0f
val maxZoom = controller.maxZoom // e.g., 10.0f
controller.setZoom(2.5f)

// Observe zoom changes
controller.zoomRatio.collectAsState().value
```

### 4. Frame Analysis (QR/Barcode)

```kotlin
class QrAnalyzer(private val onQrDetected: (String) -> Unit) : KuvaAnalyzer {
    override fun analyze(frame: KuvaFrame) {
        // Process YUV420 frame data
        // frame.y, frame.u, frame.v contain pixel data
        // frame.rotationDegrees indicates device orientation
        // Use ZXing, ML Kit, or custom decoder
        
        // Example: Basic luminance processing
        val luminance = frame.y // Y channel is luminance
        // ... QR decoding logic
    }
}

val config = KuvaConfig(
    analysis = AnalysisConfig(
        analyzers = listOf(QrAnalyzer { qrContent ->
            println("QR Code detected: $qrContent")
        }),
        backpressure = Backpressure.KeepLatest,
        targetFps = 30 // Optional FPS hint
    ),
    aspectRatio = AspectRatioHint.Square
)
```

## Installation

Add to your `commonMain` dependencies:

```kotlin
implementation("dev.nathanmkaya.kuva:kuva:1.0.0") // Not yet published
```

### Platform Requirements

**Android**: API 21+ (Android 5.0)  
**iOS**: iOS 13.0+

## Status & Error Handling

```kotlin
// Observe camera status
val status by controller.status.collectAsState()
when (status) {
    CameraStatus.Idle -> ShowStartButton()
    CameraStatus.Initializing -> ShowLoadingSpinner()
    CameraStatus.Running -> ShowCameraControls()
    is CameraStatus.Error -> ShowError(status.reason)
}

// Handle specific errors
try {
    controller.start()
} catch (e: KuvaError.PermissionDenied) {
    requestCameraPermission()
} catch (e: KuvaError.CameraInUse) {
    showCameraBusyMessage()
}
```

## Permissions

### Android
Add to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.CAMERA" />
```

### iOS
Add to `Info.plist`:
```xml
<key>NSCameraUsageDescription</key>
<string>This app uses the camera to capture photos</string>
```

## Advanced Usage

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed implementation information including:
- Internal component design and responsibilities  
- Platform-specific implementation details
- Frame analysis architecture and plugin system
- Threading model and performance considerations
- Cross-platform compatibility considerations

## Samples & Examples

Check the `:kuva-samples` module for complete examples:
- Basic camera integration with Compose Multiplatform
- Tap-to-focus with visual feedback
- All camera controls (lens, flash, zoom)  
- QR code scanning with frame analysis
- Permission handling patterns

## What's Not Included

Kuva focuses on **essential camera operations only**:

❌ Video recording  
❌ RAW/DNG/HEIF capture  
❌ Advanced camera modes (portrait, night, HDR)  
❌ Multi-lens orchestration  
❌ Built-in gallery/storage integration  
❌ Complex error taxonomies  

**Future**: These may be added as separate optional modules.

## License

Apache 2.0 License - see [LICENSE](LICENSE) for details.

---

**Kuva** — Finnish for "image" or "picture", representing the library's focus on essential camera functionality with cross-platform clarity.