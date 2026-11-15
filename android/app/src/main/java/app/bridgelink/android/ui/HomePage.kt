package app.bridgelink.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun HomePage(model: PhoneModel, actions: PhoneActions) {
    PageScroll {
        Text("BridgeLink", style = MaterialTheme.typography.headlineMedium)
        Text("Same Wi-Fi as the Mac.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConnectionDot(StatusText.dot(model.status))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(StatusText.title(model.status), style = MaterialTheme.typography.titleMedium)
                    Text(model.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BatteryCard("This phone", model.phoneBattery, Modifier.weight(1f))
            BatteryCard("Mac", model.macBattery, Modifier.weight(1f))
        }
        if (model.status.contains("key changed", ignoreCase = true)) {
            BigButton("Forget this Mac and scan again", "repair", actions::repair)
        }
        if (model.status.contains("code", ignoreCase = true)) {
            BigButton("Codes match", "confirm", actions::confirm)
        }
        Text("Quick actions", style = MaterialTheme.typography.titleMedium)
        BigButton("Scan the Mac QR", "scan", actions::scan)
        BigButton("Send a note", "go-send", { model.page = Page.Send }, tonal = true)
        BigButton("Find my Mac", "find-sound", actions::ringMac, tonal = true)
        BigButton("Mirror notifications", "mirror", actions::mirror, tonal = true)
        Text("Recent", style = MaterialTheme.typography.titleMedium)
        RecentList(model.lines)
        Text("More", style = MaterialTheme.typography.titleMedium)
        BigButton("Notifications", tonal = true, onClick = { model.page = Page.Notifications })
        BigButton("Messages", tonal = true, onClick = { model.page = Page.Messages })
        BigButton("Files", tonal = true, onClick = { model.page = Page.Files })
        BigButton("Remote", tonal = true, onClick = { model.page = Page.Remote })
        BigButton("Screen mirror", tonal = true, onClick = { model.page = Page.Mirror })
    }
}

@Composable
fun MessagesPage() {
    PageScroll {
        Text("Messages", style = MaterialTheme.typography.headlineMedium)
        EmptyState(
            Icons.Filled.Email,
            "Text messages are not on this phone yet.",
            "They arrive in a later update. You can still send a note from Send.",
        )
    }
}

@Composable
fun RemotePage(model: PhoneModel, actions: PhoneActions) {
    PageScroll {
        Text("Remote", style = MaterialTheme.typography.headlineMedium)
        Text(model.nowPlaying, style = MaterialTheme.typography.titleMedium)
        Text(
            "These buttons control music on the Mac. The Mac can also control music on this phone when notification access is on.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BigButton("Play", "media-play", { actions.media("play") })
        BigButton("Pause", "media-pause", { actions.media("pause") }, tonal = true)
        BigButton("Next", "media-next", { actions.media("next") }, tonal = true)
        BigButton("Previous", "media-prev", { actions.media("previous") }, tonal = true)
        OutlinedTextField(
            value = model.volume,
            onValueChange = { model.volume = it },
            modifier = Modifier.fillMaxWidth().testTag("volume"),
            label = { Text("Mac volume, 0 to 100") },
            singleLine = true,
        )
        BigButton("Set Mac volume", "media-volume", { actions.media("volume:" + model.volume) }, tonal = true)
        EmptyState(
            Icons.Filled.Smartphone,
            "The trackpad is not on this phone yet.",
            "Keyboard and slides arrive in a later update. Media buttons work now.",
        )
    }
}

@Composable
fun MirrorPage() {
    PageScroll {
        Text("Mirror", style = MaterialTheme.typography.headlineMedium)
        EmptyState(
            Icons.Filled.FolderOpen,
            "Screen mirror is not on this phone yet.",
            "It arrives in a later update. Notification mirror is under Alerts.",
        )
    }
}
