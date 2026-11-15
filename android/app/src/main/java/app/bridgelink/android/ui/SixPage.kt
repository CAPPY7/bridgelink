package app.bridgelink.android.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.bridgelink.android.protocol.StageSix

@Composable
fun MirrorPage(model: PhoneModel, actions: PhoneActions) {
    PageScroll {
        Text("Mirror", style = MaterialTheme.typography.headlineMedium)
        Text(model.mirrorStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(StageSix.MIRROR_STEPS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = model.mirrorQuality,
            onValueChange = { model.mirrorQuality = it },
            modifier = Modifier.fillMaxWidth().testTag("mirror-quality"),
            label = { Text("Quality: low, medium, or high") },
            singleLine = true,
        )
        BigButton("Share screen", "share-screen", actions::shareScreen)
        BigButton("Stop sharing", "stop-share", actions::stopShare, tonal = true)
        Text("Control from the Mac", style = MaterialTheme.typography.titleMedium)
        Text(StageSix.CONTROL_STEPS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigButton("Show control steps", "control-steps", actions::controlSteps, tonal = true)
        Text("Camera for the Mac", style = MaterialTheme.typography.titleMedium)
        Text(StageSix.CAM_NOTE, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigButton(
            if (model.camFacing == "front") "Use back camera" else "Use front camera",
            "cam-facing",
            { model.camFacing = if (model.camFacing == "front") "back" else "front" },
        )
        OutlinedTextField(
            value = model.camZoom,
            onValueChange = { model.camZoom = it },
            modifier = Modifier.fillMaxWidth().testTag("cam-zoom"),
            label = { Text("Zoom, 1 to 8") },
            singleLine = true,
        )
        BigButton(if (model.camFlash) "Flash on" else "Flash off", "cam-flash", { model.camFlash = !model.camFlash }, tonal = true)
        BigButton(if (model.camMic) "Mic on" else "Mic off", "cam-mic", { model.camMic = !model.camMic }, tonal = true)
        BigButton("Start camera", "cam-start", actions::startCamera)
        BigButton("Stop camera", "cam-stop", actions::stopCamera, tonal = true)
        Text("Away from home", style = MaterialTheme.typography.titleMedium)
        Text(StageSix.RELAY_STEPS, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = model.relayUrl,
            onValueChange = { model.relayUrl = it },
            modifier = Modifier.fillMaxWidth().testTag("relay-url"),
            label = { Text("Relay address, wss://") },
            singleLine = true,
        )
        BigButton("Save relay", "relay-save", actions::saveRelay, tonal = true)
        BigButton("Use away relay", "relay-use", actions::useRelay)
        EmptyState(Icons.Filled.Cast, "The picture shows on the Mac.", "This page starts it. The Mac window shows the screen.")
    }
}
