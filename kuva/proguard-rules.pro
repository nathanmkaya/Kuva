# Kuva library ProGuard rules

# Standard Android library rules
-keep public class * {
    public protected *;
}

# Preserve line numbers for debugging crashes
-keepattributes SourceFile,LineNumberTable

# Camera-specific rules
-keep class dev.nathanmkaya.kuva.** { *; }
-keepclassmembers class dev.nathanmkaya.kuva.** { *; }