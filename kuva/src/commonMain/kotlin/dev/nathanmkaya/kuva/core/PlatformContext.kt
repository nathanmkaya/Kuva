package dev.nathanmkaya.kuva.core

import androidx.compose.runtime.Composable

@Composable expect fun rememberPlatformContext(): PlatformContext

// Platform glue
expect class PlatformContext
