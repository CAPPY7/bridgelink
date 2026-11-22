package app.bridgelink.android.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun SendPage(model: PhoneModel, actions: PhoneActions) {
    PageScroll {
        Text("Send", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Text, a link, many files, or the clipboard goes to the paired Mac. This page sends files, not a whole folder. Files can be up to 4 GB.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Android shares the clipboard only while BridgeLink is open. Use Send clipboard here, the Send clipboard Quick Settings tile, or the button on the BridgeLink notification.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!model.status.startsWith("Paired")) {
            Text(
                "Pair first. Until then, Send will wait.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = model.message,
            onValueChange = { model.message = it },
            modifier = Modifier.fillMaxWidth().testTag("message"),
            label = { Text("Note for the Mac") },
            minLines = 3,
        )
        BigButton("Send note", "send", actions::sendNote)
        OutlinedTextField(
            value = model.link,
            onValueChange = { model.link = it },
            modifier = Modifier.fillMaxWidth().testTag("link"),
            label = { Text("https:// link to open on the Mac") },
            singleLine = true,
        )
        BigButton("Open link on Mac", "send-link", actions::sendLink, tonal = true)
        BigButton("Send files", "send-file", actions::pickFile, tonal = true)
        BigButton("Send clipboard", "clipboard", actions::sendClipboard, tonal = true)
        BigButton("Cancel send", "cancel-file", actions::cancelFile, tonal = true)
        Text("Clipboard history", style = MaterialTheme.typography.titleMedium)
        Text(
            "Off until you turn it on. Copies older than 24 hours are removed. The saved file is locked to this phone. Passwords are not kept.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BigButton(
            if (model.clipHistory) "Turn off clipboard history" else "Keep clipboard history",
            "clip-history",
            actions::toggleClipHistory,
        )
        BigButton("Clear history", "clip-clear", actions::clearClips, tonal = true)
        OutlinedTextField(
            value = model.clipQuery,
            onValueChange = { model.clipQuery = it },
            modifier = Modifier.fillMaxWidth().testTag("clip-query"),
            label = { Text("Search history") },
            singleLine = true,
        )
        val shown = model.clips.filter { row ->
            model.clipQuery.isBlank() || row.text.contains(model.clipQuery, ignoreCase = true)
        }.take(12)
        if (shown.isEmpty()) {
            Text("Nothing in clipboard history yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        shown.forEach { row ->
            Text((if (row.pinned) "Pinned. " else "") + row.text.replace('\n', ' '), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BigButton(if (row.pinned) "Unpin" else "Pin", "pin-" + row.id, { actions.pinClip(row.id) }, tonal = true)
            BigButton("Use", "use-" + row.id, { actions.useClip(row.id) }, tonal = true)
            BigButton("Delete", "delete-" + row.id, { actions.deleteClip(row.id) }, tonal = true)
        }
    }
}

@Composable
fun NotificationsPage(model: PhoneModel, actions: PhoneActions) {
    PageScroll {
        Text("Notifications", style = MaterialTheme.typography.headlineMedium)
        Text(
            "When this is on, the Mac shows a banner. The message text stays on the phone unless you allow it on the Mac.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BigButton("Turn on notification mirror", "mirror-page", actions::mirror)
        if (model.restricted) {
            SectionCard {
                Text("Android is blocking notification access for this sideloaded app.")
                Text(app.bridgelink.android.protocol.RestrictedHelp.stepsText())
            }
            BigButton("Show me the steps", "restricted", actions::restrictedHelp, tonal = true)
        } else {
            OutlinedTextField(
                value = model.blockApp,
                onValueChange = { model.blockApp = it },
                modifier = Modifier.fillMaxWidth().testTag("block-app"),
                label = { Text("App package to keep off the Mac") },
                singleLine = true,
            )
            BigButton("Block this app", "block-app", actions::blockNotify, tonal = true)
            EmptyState(
                Icons.Filled.Notifications,
                "No mirrored list is kept here.",
                "Banners show on the Mac. This page does not store the message text.",
            )
        }
    }
}

@Composable
fun FilesPage(model: PhoneModel, actions: PhoneActions) {
    PageScroll {
        Text("Files", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pick files and they are sent to Downloads/BridgeLink on the Mac. Drop a folder on the Mac to send it as a zip.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BigButton("Send files", "files-send", actions::pickFile)
        BigButton("Scan to Mac", "scan-doc", actions::scanDoc)
        BigButton("Cancel send", "files-cancel", actions::cancelFile, tonal = true)
        Text("Shared folder", style = MaterialTheme.typography.titleMedium)
        Text(
            if (model.folderPaused) "Paused. New files wait until you resume." else "One folder on this phone stays in sync with BridgeLink Shared on the Mac. A changed file is kept, and the other copy is saved beside it.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(model.folderLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigButton("Choose shared folder", "folder-choose", actions::chooseFolder, tonal = true)
        BigButton("Turn shared folder on", "folder-on", actions::syncFolder)
        BigButton(if (model.folderPaused) "Resume sync" else "Pause sync", "folder-pause", actions::pauseFolder, tonal = true)
        Text(
            "Scan makes a PDF when Google Play services can open the document scanner. Otherwise pick a photo. The Mac saves a scan and can open it. Other files stay closed until you press Open.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val files = model.lines.filter { line ->
            val text = line.lowercase()
            text.contains("file") || text.contains("download")
        }
        Text("File activity", style = MaterialTheme.typography.titleMedium)
        if (files.isEmpty()) {
            Text(
                "Files you send and receive will show here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RecentList(files)
        }
    }
}
