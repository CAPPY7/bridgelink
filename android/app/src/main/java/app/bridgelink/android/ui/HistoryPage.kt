package app.bridgelink.android.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun HistoryPage(model: PhoneModel, actions: PhoneActions) {
    PageScroll {
        Text("History", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Files, links, and notes sent with the Mac. Secrets are not stored. At most 2,000 rows.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = model.historyQuery,
            onValueChange = { model.historyQuery = it },
            modifier = Modifier.fillMaxWidth().testTag("history-query"),
            label = { Text("Search") },
            singleLine = true,
        )
        BigButton("Search", "history-search", actions::searchHistory)
        BigButton("Clear history", "history-clear", actions::clearHistory, tonal = true)
        if (model.history.isEmpty()) {
            Text("Nothing saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        model.history.forEach { row ->
            Text(row.direction + " " + row.kind + " · " + row.title, style = MaterialTheme.typography.titleMedium)
            if (row.status.isNotEmpty()) {
                Text(row.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                TextButton(onClick = { actions.resendHistory(row.id) }) { Text("Resend") }
                TextButton(onClick = { actions.openHistory(row.id) }) { Text("Open") }
                TextButton(onClick = { actions.deleteHistory(row.id) }) { Text("Delete") }
            }
        }
    }
}
