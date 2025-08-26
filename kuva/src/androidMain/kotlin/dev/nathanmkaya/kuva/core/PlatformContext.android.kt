package dev.nathanmkaya.kuva.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The Android-specific implementation of [PlatformContext].
 *
 * @property context The Android [Context].
 */
actual class PlatformContext(val context: Context)

/**
 * Remembers the Android-specific [PlatformContext].
 *
 * @return The Android-specific [PlatformContext].
 */
@Composable
actual fun rememberPlatformContext(): PlatformContext {
    val context = LocalContext.current
    return PlatformContext(context)
}
