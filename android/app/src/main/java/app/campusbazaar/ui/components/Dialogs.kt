package app.campusbazaar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.campusbazaar.ui.theme.CB

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CB.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft) },
        confirmButton = { QuietButton(confirm, onConfirm, danger = danger) },
        dismissButton = { QuietButton("Cancel", onDismiss) },
    )
}

val REPORT_REASONS = listOf(
    "scam" to "Scam or asking for advance payment",
    "fake_listing" to "Fake or misleading listing",
    "prohibited_item" to "Item not allowed on campus",
    "abusive" to "Harassment or abusive messages",
    "no_show" to "Didn't show up to the meetup",
    "other" to "Something else",
)

@Composable
fun ReportDialog(subject: String, onSubmit: (reason: String, details: String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CB.Surface,
        title = { Text("Report $subject", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                REPORT_REASONS.forEach { (id, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = reason == id, onClick = { reason = id }, role = Role.RadioButton).padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == id, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = CB.Ink))
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Field(details, { details = it }, "Details (optional)", singleLine = false, minLines = 2, maxLength = 1000, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { QuietButton("Send report", { reason?.let { onSubmit(it, details) } }, enabled = reason != null, danger = true) },
        dismissButton = { QuietButton("Cancel", onDismiss) },
    )
}

/** Simple single-choice dialog (used for "who bought it?"). */
@Composable
fun <T> ChoiceDialog(
    title: String,
    body: String?,
    options: List<Pair<T, String>>,
    confirm: String,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf<T?>(null) }
    var pickedSet by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CB.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft, modifier = Modifier.padding(bottom = 8.dp))
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { picked = value; pickedSet = true }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = pickedSet && picked == value, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = CB.Ink))
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (pickedSet) @Suppress("UNCHECKED_CAST") onConfirm(picked as T) }, enabled = pickedSet) {
                Text(confirm, style = MaterialTheme.typography.labelLarge, color = if (pickedSet) CB.Ink else CB.InkFaint)
            }
        },
        dismissButton = { QuietButton("Cancel", onDismiss) },
    )
}
