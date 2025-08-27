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
- **Lifecycle Binding**: Automatic start/stop with AndroidX Lifecycle
- **Gesture Support**: Built-in tap-to-focus and pinch-to-zoom

## Quick Start

### 1. Basic Setup

```kotlin
import dev.nathanmkaya.kuva.core.*
import dev.nathanmkaya.kuva.ui.Preview

@Composable
fun CameraScreen(lifecycleOwner: LifecycleOwner) {
    val context = rememberPlatformContext()
    val host = remember { PreviewHost(context) }
    val controller = remember { 
        createController(
            config = Config(
                lens = Lens.BACK,
                aspectRatio = AspectRatioHint.RATIO_16_9,
                enableTapToFocus = true
            ),
            previewHost = host
        )
    }
    
    val scope = rememberCoroutineScope()
    
    // Automatic lifecycle binding (Android)
    DisposableEffect(controller, lifecycleOwner.lifecycle) {
        val binding = controller.bindTo(lifecycleOwner.lifecycle, scope)
        onDispose { binding.close() }
    }
    
    Box(Modifier.fillMaxSize()) {
        Preview(controller, host, Modifier.fillMaxSize())
        
        // Tap-to-focus overlay
        Box(
            Modifier
                .fillMaxSize()
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

<details>
<summary>Show all imports</summary>

```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.launch
```
</details>

### 2. Using the Camera Composable

For simpler integration, use the high-level `Camera` composable:

```kotlin
import dev.nathanmkaya.kuva.core.*
import dev.nathanmkaya.kuva.ui.Camera

@Composable
fun SimpleCameraScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current // Android
    var controller by remember { mutableStateOf<Controller?>(null) }
    
    Camera(
        config = Config(
            lens = Lens.BACK,
            flash = Flash.OFF,
            aspectRatio = AspectRatioHint.RATIO_16_9,
            enableTapToFocus = true
        ),
        lifecycleOwner = lifecycleOwner,
        modifier = Modifier.fillMaxSize(),
        onControllerReady = { controller = it }
    )
}
```

> **Note**: On iOS, lifecycle binding is handled automatically by the library.

### 3. Photo Capture

```kotlin
// Simple capture
val result = controller.capturePhoto()
println("Captured ${result.bytes.size} bytes, ${result.width}x${result.height}")

// Access EXIF orientation (Android) or embedded orientation (iOS)
val orientationTag = result.exifOrientationTag // Android: ExifInterface constant, iOS: null
val rotationDegrees = result.rotationDegrees   // Usually 0 (orientation in EXIF)
val mimeType = result.mimeType                 // "image/jpeg"
```

### 4. Camera Controls

```kotlin
// Lens switching
val newLens = controller.switchLens() // Returns Lens.FRONT or Lens.BACK

// Flash modes
controller.setFlash(Flash.AUTO)
controller.setTorch(enabled = true)

// Zoom control with bounds
val minZoom = controller.minZoom // e.g., 1.0f
val maxZoom = controller.maxZoom // e.g., 10.0f
controller.setZoom(2.5f)

// Observe zoom changes
val currentZoom by controller.zoomRatio.collectAsState()
```

### 5. Camera Controls

Access camera controls through the controller:

```kotlin
// Basic controls
val newLens = controller.switchLens()
controller.setFlash(Flash.AUTO)
controller.setTorch(enabled = true)
controller.setZoom(2.5f)

// Observe state
val status by controller.status.collectAsState()
val zoom by controller.zoomRatio.collectAsState()
```

For a complete example with UI controls, see the `:kuva-samples` module.

## Configuration Options

- **lens**: `Lens.BACK` or `Lens.FRONT`
- **flash**: `Flash.OFF`, `Flash.ON`, `Flash.AUTO`
- **aspectRatio**: `DEFAULT`, `RATIO_4_3`, `RATIO_16_9`, `SQUARE`
- **enableTapToFocus**: Enable/disable tap-to-focus (default: `true`)
- **enforceCaptureAspectRatio**: Match capture to preview aspect ratio (default: `false`)

## Status & Error Handling

Monitor camera status and handle common errors:

```kotlin
val status by controller.status.collectAsState()
// Status types: Idle, Initializing, Running, Error

try {
    controller.start()
} catch (e: Error.PermissionDenied) {
    // Request camera permission
} catch (e: Error.CameraInUse) {
    // Handle camera in use by another app
}
```

For complete error handling patterns, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Installation

**Coming Soon**: Kuva will be published to Maven Central.

For now, you can use the library by including it as a Git submodule or composite build:

```kotlin
// settings.gradle.kts
includeBuild("path/to/kuva")

// build.gradle.kts
implementation(project(":kuva"))
```

### Platform Requirements

**Android**: API 21+ (Android 5.0)  
**iOS**: iOS 13.0+

### Permissions

#### Android
Add to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.CAMERA" />
```

**Note**: On Android 6.0+ you must also request CAMERA permission at runtime before starting the camera.

#### iOS
Add to `Info.plist`:
```xml
<key>NSCameraUsageDescription</key>
<string>This app uses the camera to capture photos</string>
```

## API Documentation

For detailed API reference, see:
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Complete architecture and API details
- **API Documentation** - Generated API docs (coming soon)

## Sample Application

Check the `:kuva-samples` module for a complete example featuring:
- Basic camera integration with Compose Multiplatform
- All camera controls (lens switching, flash, torch, zoom)
- Tap-to-focus with visual feedback
- Pinch-to-zoom gesture support
- Status monitoring and error handling
- Permission handling patterns

Run the sample:
```bash
# Android
./gradlew :kuva-samples:installDebug

# iOS (requires Xcode)
./gradlew :kuva-samples:iosApp
```

## Architecture

Kuva provides a unified camera API across Android (CameraX) and iOS (AVFoundation) with clean separation between business logic and platform implementations.

For detailed architecture information, see [ARCHITECTURE.md](ARCHITECTURE.md).

## What's Not Included

Kuva focuses on **essential camera operations only**:

❌ Video recording  
❌ RAW/DNG/HEIF capture  
❌ Advanced camera modes (portrait, night, HDR)  
❌ Multi-lens orchestration  
❌ Built-in gallery/storage integration  
❌ Frame analysis / ML integration
❌ Complex error taxonomies  

**Future**: These may be added as separate optional modules.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Run `./gradlew build` to ensure everything works
6. Submit a pull request

## License

MIT License - see [LICENSE](LICENSE) for details.

Copyright (c) 2025 Nathan Mkaya

---

**Kuva** — Finnish for "image" or "picture", representing the library's focus on essential camera functionality with cross-platform clarity.