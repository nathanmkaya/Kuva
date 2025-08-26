package dev.nathanmkaya.kuva.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

actual class PlatformContext(val context: Context)

@Composable
actual fun rememberPlatformContext(): PlatformContext {
    val context = LocalContext.current
    return PlatformContext(context)
}
