package dev.nathanmkaya.kuva.samples

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.camera.CAMERA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App(modifier: Modifier = Modifier, controller: PermissionsController) {
    MaterialTheme {
        val coroutineScope: CoroutineScope = rememberCoroutineScope()
        var isCameraPermissionGranted: Boolean by remember { mutableStateOf(false) }

        LaunchedEffect(controller) {
            isCameraPermissionGranted =
                controller.getPermissionState(Permission.CAMERA) == PermissionState.Granted
        }

        CameraScreen(
            modifier = modifier,
            hasPermission = isCameraPermissionGranted,
            onRequestPermission = {
                coroutineScope.launch { controller.providePermission(Permission.CAMERA) }
            },
            onResult = { println("Photo result: $it") },
        )
    }
}
