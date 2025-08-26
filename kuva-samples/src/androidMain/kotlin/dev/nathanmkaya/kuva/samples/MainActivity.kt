package dev.nathanmkaya.kuva.samples

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.compose.BindEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val permissionController = PermissionsController(context)
            BindEffect(permissionController)
            Scaffold { innerPadding ->
                App(
                    modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                    permissionController,
                )
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    // App()
}
