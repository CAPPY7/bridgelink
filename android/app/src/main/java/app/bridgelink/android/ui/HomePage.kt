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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.shape.RoundedCornerShape

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
fun MessagesPage(model: PhoneModel, actions: PhoneActions) {
    PageScroll {
        Text("Messages", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Read and reply from the Mac, or from this phone. Android may ask you to allow restricted settings before SMS works on a sideloaded app.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BigButton(if (model.smsOn) "Texts are on" else "Turn texts on", "sms-on", actions::enableSms)
        BigButton("Refresh texts", "sms-refresh", actions::refreshSms, tonal = true)
        BigButton("Show SMS steps", "sms-steps", actions::smsSteps, tonal = true)
        if (model.simCount > 1) {
            OutlinedTextField(
                value = model.sim,
                onValueChange = { model.sim = it },
                modifier = Modifier.fillMaxWidth().testTag("sms-sim"),
                label = { Text("SIM, 0 or 1") },
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = model.smsTo,
            onValueChange = { model.smsTo = it },
            modifier = Modifier.fillMaxWidth().testTag("sms-to"),
            label = { Text("Phone number") },
            singleLine = true,
        )
        OutlinedTextField(
            value = model.smsBody,
            onValueChange = { model.smsBody = it },
            modifier = Modifier.fillMaxWidth().testTag("sms-body"),
            label = { Text("Message") },
            minLines = 2,
        )
        BigButton("Send text", "sms-send", actions::sendSms)
        if (model.messages.isEmpty()) {
            EmptyState(
                Icons.Filled.Email,
                "No texts yet.",
                "Turn texts on, allow SMS, then tap Refresh. New texts also show on the Mac.",
            )
        } else {
            model.messages.take(30).forEach { sms ->
                Text((if (sms.incoming) "From " else "To ") + sms.address, style = MaterialTheme.typography.titleSmall)
                Text(sms.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun RemotePage(model: PhoneModel, actions: PhoneActions) {
    DisposableEffect(Unit) {
        actions.media("watch")
        onDispose { actions.media("idle") }
    }
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
        Text("Trackpad", style = MaterialTheme.typography.titleMedium)
        Text(
            if (model.trackpadOn) "On. Drag to move the Mac pointer. Two fingers scroll." else "Off. The Mac must allow BridgeLink under Accessibility.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BigButton(if (model.trackpadOn) "Turn trackpad off" else "Turn trackpad on", "trackpad", actions::toggleTrackpad)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .testTag("trackpad")
                .pointerInput(model.trackpadOn, model.dragMode) {
                    if (!model.trackpadOn) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var scrolling = false
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                scrolling = true
                                val delta = pressed[0].positionChange()
                                actions.pointer("scroll", 0, -delta.y.toInt(), "")
                            } else if (!scrolling) {
                                val delta = pressed[0].positionChange()
                                if (delta.x != 0f || delta.y != 0f) {
                                    moved = true
                                    val kind = if (model.dragMode) "drag" else "move"
                                    actions.pointer(kind, delta.x.toInt(), delta.y.toInt(), "")
                                }
                            }
                            pressed.forEach { it.consume() }
                        }
                        if (scrolling) {
                            // A two-finger scroll is not a click.
                        } else if (moved && model.dragMode) {
                            actions.pointer("drag_end", 0, 0, "")
                        } else if (!moved) {
                            actions.pointer("tap", 0, 0, "")
                        }
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(Modifier.fillMaxWidth().height(180.dp).padding(12.dp)) {
                Text("Drag here. A short press clicks. Two fingers scroll.")
            }
        }
        BigButton("Right click", "right-click", { actions.pointer("right", 0, 0, "") }, tonal = true)
        BigButton(if (model.dragMode) "Dragging is on" else "Next drag holds the click", "drag-mode", { model.dragMode = !model.dragMode }, tonal = true)
        BigButton("Slide next", "slide-next", { actions.pointer("slide", 0, 0, "next") }, tonal = true)
        BigButton("Slide previous", "slide-prev", { actions.pointer("slide", 0, 0, "prev") }, tonal = true)
        BigButton("Laser pointer", "laser", { actions.pointer("laser", 1, 0, "") }, tonal = true)
        BigButton("Laser off", "laser-off", { actions.pointer("laser", 0, 0, "") }, tonal = true)
        OutlinedTextField(
            value = model.typeText,
            onValueChange = { model.typeText = it },
            modifier = Modifier.fillMaxWidth().testTag("type-text"),
            label = { Text("Type on the Mac") },
            singleLine = true,
        )
        BigButton("Type", "type-send", { actions.pointer("key", 0, 0, model.typeText) })
    }
}
