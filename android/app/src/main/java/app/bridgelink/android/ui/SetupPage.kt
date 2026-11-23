package app.bridgelink.android.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.bridgelink.android.protocol.SetupSteps

/** One permission at a time. Nothing is asked until Allow is tapped. Skip is always there. */
@Composable
fun SetupPage(model: PhoneModel, actions: PhoneActions) {
    val steps = SetupSteps.forSdk(Build.VERSION.SDK_INT)
    val index = model.setupIndex.coerceIn(0, steps.size)
    PageScroll {
        Text("Set up BridgeLink", style = MaterialTheme.typography.headlineMedium)
        if (index >= steps.size) {
            Text("You can change these any time in Settings > Permissions.")
            BigButton("I'm ready", "setup-done", actions::finishSetup)
        } else {
            val step = steps[index]
            Text(SetupSteps.title(step), style = MaterialTheme.typography.titleMedium)
            Text(SetupSteps.why(step), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Step ${index + 1} of ${steps.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigButton("Allow", "setup-allow", { actions.allowSetup(step) })
            BigButton("Skip", "setup-skip", actions::skipSetup, tonal = true)
        }
    }
}
