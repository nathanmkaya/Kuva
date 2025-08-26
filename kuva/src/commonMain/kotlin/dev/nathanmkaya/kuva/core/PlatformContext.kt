package dev.nathanmkaya.kuva.core

import androidx.compose.runtime.Composable

/**
 * Remembers the platform-specific context.
 *
 * @return The platform-specific context.
 */
@Composable expect fun rememberPlatformContext(): PlatformContext

/**
 * A platform-specific context object. This is used to provide platform-specific dependencies to the
 * camera controller.
 */
expect class PlatformContext
