# Kuva library consumer ProGuard rules

# Keep camera-related classes that might be accessed via reflection
-keep class dev.nathanmkaya.kuva.** { *; }

# CameraX rules
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Coroutines rules for camera operations
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}