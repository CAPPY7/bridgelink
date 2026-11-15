package app.bridgelink.android.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun SendPage(model: PhoneModel, actions: PhoneActions) {
    PageScroll {
        Text("Send", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Text, a link, a file, or the clipboard goes to the paired Mac.",
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
        BigButton("Send a file", "send-file", actions::pickFile, tonal = true)
        BigButton("Send clipboard", "clipboard", actions::sendClipboard, tonal = true)
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
            "Pick a file and it is sent to Downloads/BridgeLink on the Mac.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BigButton("Send a file", "files-send", actions::pickFile)
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
