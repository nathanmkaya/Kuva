package dev.nathanmkaya.kuva.samples

import androidx.compose.ui.window.ComposeUIViewController
import dev.icerock.moko.permissions.ios.PermissionsController

fun MainViewController() = ComposeUIViewController {
    val permissionsController = PermissionsController()
    App(controller = permissionsController)
}
