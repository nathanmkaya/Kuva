package dev.nathanmkaya.kuva.samples

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nathanmkaya.kuva.CameraController
import dev.nathanmkaya.kuva.ui.PlatformPreviewHost

/**
 * Simple camera screen demonstrating basic Kuva usage.
 * 
 * This composable shows the minimal code needed to implement
 * a working camera with preview (capture functionality will be added once
 * the basic preview is working).
 */
@Composable
fun CameraScreen() {
    val controller = remember { CameraController() }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Camera preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            PlatformPreviewHost(
                controller = controller,
                modifier = Modifier.fillMaxSize()
            )
            
            // Status overlay
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Kuva Camera Preview",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Simple info card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "This demonstrates the Kuva camera library integration. Full controls will be added once the basic preview is working correctly.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}