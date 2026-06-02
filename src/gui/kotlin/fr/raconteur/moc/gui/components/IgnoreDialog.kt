package fr.raconteur.moc.gui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.raconteur.moc.gui.IgnoreKind

@Composable
fun IgnoreDialog(onIgnore: (IgnoreKind) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ignore entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ignore this entry for how long?")
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onIgnore(IgnoreKind.Session) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("1  Until next patch (session)") }
                OutlinedButton(
                    onClick = { onIgnore(IgnoreKind.Value) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("2  Until value changes") }
                OutlinedButton(
                    onClick = { onIgnore(IgnoreKind.Permanent) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("3  Permanently") }
            }
        },
        buttons = {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
