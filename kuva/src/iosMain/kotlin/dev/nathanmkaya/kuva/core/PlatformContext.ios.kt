package dev.nathanmkaya.kuva.core

import androidx.compose.runtime.Composable

/** The iOS-specific implementation of [PlatformContext]. */
actual class PlatformContext

/**
 * Remembers the iOS-specific [PlatformContext].
 *
 * @return The iOS-specific [PlatformContext].
 */
@Composable actual fun rememberPlatformContext(): PlatformContext = PlatformContext()
