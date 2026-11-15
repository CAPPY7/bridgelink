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
        BigButton(if (model.lockOn) "Turn leave-lock off" else "Turn leave-lock on", "lock-toggle", actions::toggleLock)
        BigButton(if (model.atDesk) "I left the desk" else "I am at my desk", "at-desk", actions::toggleDesk, tonal = true)
        BigButton("Save lock settings", "lock-save", actions::saveLock, tonal = true)
        Text("Automations", style = MaterialTheme.typography.titleMedium)
        Text(
            "When and then use short names. When: connect, battery_low, home_wifi, sms_code, file_in, clip, notify, leave. Then: open_app, alert, start_sync, copy_code, ring, pause_sync, note, none. Eight rules at most.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = model.ruleWhen,
            onValueChange = { model.ruleWhen = it },
            modifier = Modifier.fillMaxWidth().testTag("rule-when"),
            label = { Text("When") },
            singleLine = true,
        )
        OutlinedTextField(
            value = model.ruleThen,
            onValueChange = { model.ruleThen = it },
            modifier = Modifier.fillMaxWidth().testTag("rule-then"),
            label = { Text("Then") },
            singleLine = true,
        )
        OutlinedTextField(
            value = model.ruleExtra,
            onValueChange = { model.ruleExtra = it },
            modifier = Modifier.fillMaxWidth().testTag("rule-extra"),
            label = { Text("Extra, such as an app name or home Wi-Fi name") },
            singleLine = true,
        )
        BigButton("Add automation", "rule-add", actions::addRule)
        model.rules.forEach { rule ->
            Text((if (rule.enabled) "On. " else "Off. ") + rule.whenLabel + " → " + rule.thenLabel)
            BigButton("Toggle " + rule.whenLabel, "rule-" + rule.id, { actions.toggleRule(rule.id) }, tonal = true)
        }
        if (model.version.isNotEmpty()) {
            Text("BridgeLink " + model.version, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Recent", style = MaterialTheme.typography.titleMedium)
        RecentList(model.lines)
    }
}
