# Kuva — Architecture

**System Design & API Guidance**

This document describes the architectural design of Kuva, explaining how components interact, why API interfaces are shaped the way they are, and how to use the library effectively. For platform-specific implementation details, see the developer guides.

## System Overview

Kuva is designed around three core abstractions that work together to provide a minimal, predictable camera experience:

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   KuvaConfig    │───▶│  KuvaController  │◄──▶│ KuvaPreviewHost │
│                 │    │                  │    │                 │
│ • Lens          │    │ • State Machine  │    │ • Native View   │
│ • Flash         │    │ • Lifecycle      │    │ • Preview       │
│ • AspectRatio   │    │ • Operations     │    │ • Surface       │
│ • TapToFocus    │    │ • Error Handling │    │                 │
│ • Analysis      │    │ • Observers      │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │ Platform Adapter │
                       │                  │
                       │ Android: CameraX │
                       │ iOS: AVFoundation│
                       └──────────────────┘
```

**Design Goals:**
- **Minimal Surface**: Essential camera operations only
- **Platform Parity**: Identical behavior across Android/iOS where possible
- **Progressive Disclosure**: Simple defaults, advanced configuration when needed
- **Extension Points**: Plugin system for frame analysis and future features

## Core Abstractions & Responsibilities

### KuvaController - The Orchestrator

The Controller is the primary entry point and state coordinator. It owns the camera session, manages lifecycle, and provides reactive state updates.

**Key Responsibilities:**
- **State Management**: Maintains camera state machine and broadcasts changes
- **Operation Coordination**: Serializes camera operations (start, capture, configure)
- **Error Handling**: Maps platform errors to unified error taxonomy
- **Resource Management**: Ensures proper cleanup and lifecycle coordination

**Interface Design Rationale:**
```kotlin
interface KuvaController {
    val status: StateFlow<CameraStatus>     // Reactive state for UI
    val zoomRatio: StateFlow<Float>         // Reactive zoom for controls
    
    suspend fun start()                     // Async, idempotent
    suspend fun capturePhoto(): PhotoResult // Returns result directly
    suspend fun tapToFocus(x: Float, y: Float) // Normalized coordinates
}
```

**Why Suspending Functions?** Operations like camera startup and photo capture are inherently async and can fail. Suspend functions provide natural error propagation and cancellation support.

**Why StateFlow?** Camera state and controls need reactive updates for UI. StateFlow provides hot, stateful updates that survive configuration changes.

### KuvaConfig - Configuration Strategy

Configuration uses an immutable value object pattern with sensible defaults and progressive disclosure.

**Design Principles:**
- **Immutable**: Thread-safe, predictable behavior
- **Defaulted**: Works with zero configuration for 80% of use cases
- **Composable**: Analysis, flash, aspect ratio can be mixed independently
- **Validated**: Invalid combinations fail fast at creation time

```kotlin
data class KuvaConfig(
    val lens: Lens = Lens.Back,                           // Most common default
    val aspectRatio: AspectRatioHint = AspectRatioHint.Default, // Platform native
    val enableTapToFocus: Boolean = true,                 // Expected behavior
    val analysis: AnalysisConfig? = null                  // Opt-in complexity
)
```

**Why Not a Builder?** Simple data classes with defaults are more Kotlin-idiomatic and reduce API surface area.

### KuvaPreviewHost - Platform Abstraction

The PreviewHost provides a thin wrapper around platform preview views, hiding platform differences while maintaining performance.

**Abstraction Strategy:**
- **Opaque Handle**: Wraps platform view without exposing implementation
- **Lifecycle Aware**: Handles platform-specific setup/teardown automatically
- **Compose Native**: Integrates naturally with Compose Multiplatform

## State Machine & Error Model

### Camera State Transitions

Kuva uses an explicit state machine to make camera lifecycle predictable:

```
Idle ──start()──▶ Initializing ──success──▶ Running
 ▲                     │                      │
 │                   error                  stop()
 │                     ▼                      ▼
 └────────────── Error ◀─────error───── Idle
```

**State Guarantees:**
- **Idempotent Operations**: `start()` on Running controller has no effect
- **Clean Transitions**: `stop()` always succeeds and releases resources
- **Error Recovery**: From Error state, only `start()` (retry) or `stop()` (cleanup) allowed

### Unified Error Taxonomy

Platform-specific errors are mapped to a unified hierarchy for consistent handling:

```kotlin
sealed class KuvaError : Exception {
    object PermissionDenied : KuvaError()     // Recoverable: request permission
    object CameraInUse : KuvaError()          // Recoverable: retry later
    class Hardware(msg: String) : KuvaError() // May be recoverable
    class Unknown(msg: String) : KuvaError()  // Log and report
}
```

**Error Strategy:**
- **Typed Recovery**: Different errors suggest different recovery strategies
- **Platform Mapping**: Android SecurityException → PermissionDenied
- **Graceful Degradation**: Library continues working after non-fatal errors

## Data Flow Architecture

### Frame Processing Pipeline

```
Device Camera → Platform Adapter → Preview Surface
     │                               ↓
     └─→ Analysis Pipeline ─→ KuvaAnalyzer Plugins
```

**Flow Characteristics:**
- **Backpressure Aware**: Configurable frame dropping to prevent memory buildup
- **Thread Isolation**: Analysis runs on background threads, never blocks preview
- **Plugin Sandboxing**: Individual analyzer failures don't crash the pipeline

### Capture Flow

```
capturePhoto() → Platform Capture → Orientation Fix → PhotoResult
```

**Cross-Platform Guarantees:**
- **Consistent Orientation**: EXIF metadata ensures proper rotation on both platforms
- **Memory Efficient**: Direct byte array return, no intermediate file I/O
- **Error Propagation**: Platform failures mapped to KuvaError hierarchy

## Cross-Platform Strategy

### Capability Normalization

Kuva handles platform differences through capability-aware configuration:

**Approach:**
- **Common Subset**: API surface covers features available on both platforms
- **Graceful Degradation**: Unsupported features degrade predictably
- **Feature Detection**: Runtime capability queries where needed

**Examples:**
- **Aspect Ratios**: Hints rather than guarantees (platforms may adjust)
- **Flash Modes**: Auto/On/Off mapped to platform equivalents
- **Zoom Ranges**: Exposed as platform-native min/max bounds

### Platform Abstraction Boundaries

```
┌─── Common API ───┐
│ KuvaController   │ ← User-facing interface
│ KuvaConfig       │
│ PhotoResult      │
└──────────────────┘
┌─── Adapters ─────┐
│ Android: CameraX │ ← Platform-specific implementation
│ iOS: AVFoundation│
└──────────────────┘
```

**What's Hidden:**
- Threading models (CameraX executors, AVFoundation queues)
- Memory management (ImageProxy, CVPixelBuffer)
- Platform error types (CameraAccessException, NSError)

**What's Exposed:**
- Unified state model and error taxonomy
- Normalized coordinate systems (0..1 for tap-to-focus)
- Cross-platform result types (PhotoResult, KuvaFrame)

## Extension Architecture

### Analysis Plugin System

Kuva supports real-time frame analysis through a plugin interface:

```kotlin
interface KuvaAnalyzer {
    fun analyze(frame: KuvaFrame)  // Called on background thread
    fun close() {}                 // Cleanup when analysis stops
}
```

**Plugin Contract:**
- **Threading**: `analyze()` called serially on background thread
- **Performance**: Must complete within frame budget (typically 33ms)
- **Memory**: ByteArrays may be reused; copy data if retaining
- **Error Handling**: Plugin exceptions don't crash analysis pipeline

### Configuration & Backpressure

```kotlin
data class AnalysisConfig(
    val analyzers: List<KuvaAnalyzer>,
    val backpressure: Backpressure = Backpressure.KeepLatest,
    val reuseBuffers: Boolean = true
)
```

**Backpressure Strategy:**
- **KeepLatest**: Drop older frames for low latency (recommended for QR scanning)
- **BlockProducer**: Process all frames for completeness (use for video recording)

## Integration Patterns

### Compose Integration

Kuva integrates naturally with Compose Multiplatform lifecycle:

```kotlin
@Composable
fun CameraScreen(lifecycleOwner: LifecycleOwner) {
    val host = remember { KuvaPreviewHost(rememberPlatformContext()) }
    val controller = remember { createKuvaController(KuvaConfig(), host) }
    
    // Automatic lifecycle binding
    DisposableEffect(controller, lifecycleOwner.lifecycle) {
        val binding = controller.bindTo(lifecycleOwner.lifecycle, rememberCoroutineScope())
        onDispose { binding.close() }
    }
    
    // Reactive UI based on camera state
    val status by controller.status.collectAsState()
    when (status) {
        CameraStatus.Running -> ShowCameraControls()
        CameraStatus.Error -> ShowErrorMessage()
        // ...
    }
}
```

### Lifecycle Coordination

**AndroidX Lifecycle Integration:**
- **Automatic Start/Stop**: Binds to lifecycle events (ON_START/ON_STOP)
- **Configuration Flexibility**: Choose when to start (ON_START vs ON_RESUME)
- **Clean Teardown**: AutoCloseable binding ensures resource cleanup

## Performance & Resource Management

### Resource Strategy

**Memory Management:**
- **Buffer Reuse**: Analysis pipeline can reuse frame buffers
- **Automatic Cleanup**: Controllers implement AutoCloseable for deterministic cleanup
- **Leak Prevention**: StateFlow and coroutine scopes tied to lifecycle

**Performance Principles:**
- **Non-blocking Preview**: UI thread never waits for camera operations
- **Bounded Analysis**: Frame analysis has time and memory budgets
- **Lazy Initialization**: Heavy resources allocated only when needed

### Concurrency Guarantees

**Thread Safety:**
- **Public API**: All methods safe to call from main thread
- **StateFlow Emissions**: Thread-safe, can be collected from any dispatcher
- **Analysis Callbacks**: Delivered on background threads with serial execution

## Security & Privacy

### Permission Model

**Runtime Permissions:**
- **Camera**: Required for all operations
- **Storage**: Not required (library returns byte arrays, doesn't save files)
- **Graceful Failure**: Permission denied maps to typed error for UI handling

**Privacy Defaults:**
- **No Persistence**: Library doesn't save photos or analysis data
- **Opt-in Storage**: Applications must explicitly handle file saving
- **Background Behavior**: Camera stops automatically when app backgrounds

## API Versioning & Stability

### Compatibility Guarantees

**Public API:**
- **Semantic Versioning**: Breaking changes only in major versions
- **Deprecation Policy**: 6-month warning before removal
- **Source Compatibility**: Kotlin binary compatibility maintained

**Extension SPI:**
- **KuvaAnalyzer Interface**: Stable within major version
- **Frame Format**: Cross-platform YUV420 layout guaranteed
- **Threading Contract**: Analyzer execution model won't change

### Extension Points

**Current Extension Interfaces:**
- **Frame Analysis**: `KuvaAnalyzer` for real-time processing
- **Lifecycle Binding**: Custom lifecycle integration patterns

**Future Extension Areas:**
- **Capture Formats**: Additional output formats (HEIF, RAW)
- **Advanced Controls**: Manual exposure, focus, white balance
- **Multi-Camera**: Concurrent camera session management

## Design Trade-offs & Non-Goals

### Conscious Limitations

**What's Not Included:**
- **Video Recording**: Separate concern, would double API surface
- **Advanced Camera Modes**: Portrait, Night, HDR require platform-specific tuning
- **Built-in ML Models**: Keep library lean, analyzers handle ML integration
- **File Management**: Applications better suited for storage decisions

**Trade-offs Made:**
- **Simplicity vs Configurability**: Chose sensible defaults over comprehensive options
- **Performance vs Safety**: Chose typed errors over silent failures
- **API Surface vs Features**: Chose minimal interface over feature completeness

### Architectural Decisions

**Why Preview/Controller/Config Pattern?**
- Separates concerns cleanly (view, orchestration, configuration)
- Maps naturally to platform concepts (PreviewView, Camera, CameraSelector)
- Provides clear extension points without tight coupling

**Why State Machine Over Callbacks?**
- Makes lifecycle predictable and debuggable
- Prevents invalid state transitions
- Simplifies error recovery and retry logic

**Why Suspend Functions Over Callbacks?**
- Natural error propagation and cancellation
- Compose-friendly async patterns
- Eliminates callback hell for sequential operations

---

This architecture enables a **minimal, predictable camera library** that provides essential functionality with clear extension points, while maintaining platform parity and performance.