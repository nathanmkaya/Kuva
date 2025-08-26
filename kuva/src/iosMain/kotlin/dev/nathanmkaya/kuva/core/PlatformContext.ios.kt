package dev.nathanmkaya.kuva.core

import androidx.compose.runtime.Composable

actual class PlatformContext

@Composable actual fun rememberPlatformContext(): PlatformContext = PlatformContext()
