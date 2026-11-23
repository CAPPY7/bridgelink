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
        BigButton("Switch to this saved Mac", "use-saved-mac", actions::useSavedMac, tonal = true)
        Text("Sharing", style = MaterialTheme.typography.titleMedium)
        BigButton("Clipboard sync on or off", "clip-sync", actions::toggleClipboard, tonal = true)
        BigButton("Always open links", "links-always", actions::toggleLinks, tonal = true)
        BigButton("Sync Do Not Disturb", "dnd", actions::dndAccess, tonal = true)
        Text("Phone", style = MaterialTheme.typography.titleMedium)
        BigButton("Keep me connected", "battery", actions::batterySettings, tonal = true)
        BigButton("Send diagnostics", "diagnostics", actions::diagnostics, tonal = true)
        BigButton("Check for update", "check-update", actions::checkUpdate, tonal = true)
        if (model.updateLine.isNotEmpty()) {
            Text(model.updateLine, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BigButton("Show setup again", "show-setup", actions::showSetup, tonal = true)
        BigButton("Reset BridgeLink", "reset-app", actions::resetApp, tonal = true)
        BigButton("Stop Mac ringing", "stop-ring", actions::stopRing, tonal = true)
        Text("Lock the Mac when you leave", style = MaterialTheme.typography.titleMedium)
        Text(
            if (model.lockOn) "On. The phone watches the Mac Bluetooth signal." else "Off.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "When the signal stays weak, the Mac locks. This phone never unlocks the Mac. Charging, or At my desk, skips the lock.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = model.lockFar,
            onValueChange = { model.lockFar = it },
            modifier = Modifier.fillMaxWidth().testTag("lock-far"),
            label = { Text("How far, 40 close to 95 far") },
            singleLine = true,
        )
        OutlinedTextField(
            value = model.lockDelay,
            onValueChange = { model.lockDelay = it },
            modifier = Modifier.fillMaxWidth().testTag("lock-delay"),
            label = { Text("Wait this many seconds") },
            singleLine = true,
        )
        Text("Off. Turn on to lock the Mac when your phone walks away.")
        BigButton(if (model.lockOn) "Turn leave-lock off" else "Turn leave-lock on", "lock-toggle", actions::toggleLock)
        BigButton(if (model.atDesk) "I left the desk" else "I am at my desk", "at-desk", actions::toggleDesk, tonal = true)
        BigButton("Save lock settings", "lock-save", actions::saveLock, tonal = true)
        if (model.version.isNotEmpty()) {
            Text("BridgeLink " + model.version, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Recent", style = MaterialTheme.typography.titleMedium)
        RecentList(model.lines)
    }
}
