package app.bridgelink.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Shown once, until the person taps I'm ready. Other tabs stay available. */
@Composable
fun SetupPage(actions: PhoneActions) {
    PageScroll {
        Text("Set up BridgeLink", style = MaterialTheme.typography.headlineMedium)
        Text(
            "This takes a minute. After it, scan the Mac QR from Settings.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("1. Allow notifications when Android asks. That keeps the connection notice visible.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("2. Turn off battery optimization so the phone does not kill the link.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigButton("Keep me connected", "setup-battery", actions::batterySettings)
        Text(
            "On Xiaomi, Oppo, Vivo, Samsung, OnePlus, or Huawei, also allow autostart for BridgeLink.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "3. Android 13 and newer may hide notification access, texts, or screen control until you allow restricted settings. Settings has the steps.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BigButton("I'm ready", "setup-done", actions::finishSetup)
    }
}
