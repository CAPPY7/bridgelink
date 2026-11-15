package app.bridgelink.android.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun SettingsPage(model: PhoneModel, actions: PhoneActions) {
    PageScroll {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text(model.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Pair", style = MaterialTheme.typography.titleMedium)
        BigButton("Scan the Mac QR", "scan-settings", actions::scan)
        OutlinedTextField(
            value = model.pair,
            onValueChange = { model.pair = it },
            modifier = Modifier.fillMaxWidth().testTag("pair"),
            label = { Text("Or paste the pairing link") },
            singleLine = true,
        )
        BigButton("Use pairing link", "apply-link", actions::applyLink, tonal = true)
        BigButton("Find Mac on Wi-Fi", "find-mac", actions::findMac, tonal = true)
        OutlinedTextField(
            value = model.host,
            onValueChange = { model.host = it },
            modifier = Modifier.fillMaxWidth().testTag("host"),
            label = { Text("Mac address") },
            singleLine = true,
        )
        OutlinedTextField(
            value = model.port,
            onValueChange = { model.port = it },
            modifier = Modifier.fillMaxWidth().testTag("port"),
            label = { Text("Port") },
            singleLine = true,
        )
        OutlinedTextField(
            value = model.pin,
            onValueChange = { model.pin = it },
            modifier = Modifier.fillMaxWidth().testTag("pin"),
            label = { Text("PIN from the QR. Leave empty to match a code.") },
            singleLine = true,
        )
        BigButton("Connect", "connect", actions::connect)
        BigButton("Codes match", "confirm-settings", actions::confirm, tonal = true)
        BigButton("Forget this Mac", "forget", actions::forget, tonal = true)
        OutlinedTextField(
            value = model.macName,
            onValueChange = { model.macName = it },
            modifier = Modifier.fillMaxWidth().testTag("mac-name"),
            label = { Text("Name for this Mac") },
            singleLine = true,
        )
        BigButton("Rename this Mac", "rename-mac", actions::renameMac, tonal = true)
        Text("Sharing", style = MaterialTheme.typography.titleMedium)
        BigButton("Clipboard sync on or off", "clip-sync", actions::toggleClipboard, tonal = true)
        BigButton("Always open links", "links-always", actions::toggleLinks, tonal = true)
        BigButton("Sync Do Not Disturb", "dnd", actions::dndAccess, tonal = true)
        Text("Phone", style = MaterialTheme.typography.titleMedium)
        BigButton("Keep me connected", "battery", actions::batterySettings, tonal = true)
        BigButton("Send diagnostics", "diagnostics", actions::diagnostics, tonal = true)
        BigButton("Stop ringing", "stop-ring", actions::stopRing, tonal = true)
        if (model.version.isNotEmpty()) {
            Text("BridgeLink " + model.version, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Recent", style = MaterialTheme.typography.titleMedium)
        RecentList(model.lines)
    }
}
