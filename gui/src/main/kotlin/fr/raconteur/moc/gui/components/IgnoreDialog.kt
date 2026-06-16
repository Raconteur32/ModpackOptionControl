package fr.raconteur.moc.gui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.raconteur.moc.gui.IgnoreKind

@Composable
fun IgnoreDialog(
    selection: Int,
    isFile: Boolean,
    onIgnore: (IgnoreKind) -> Unit,
    onIgnoreDir: () -> Unit,
    onDismiss: () -> Unit
) {
    val standardOptions = listOf(
        IgnoreKind.Session   to "Until next patch (session)",
        IgnoreKind.Value     to "Until value changes",
        IgnoreKind.Permanent to "Permanently"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ignore entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ignore this entry for how long?")
                Spacer(Modifier.height(4.dp))
                standardOptions.forEachIndexed { i, (kind, label) ->
                    if (i == selection) {
                        Button(
                            onClick = { onIgnore(kind) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = { onIgnore(kind) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                }
                if (isFile) {
                    val dirIdx = standardOptions.size
                    if (dirIdx == selection) {
                        Button(
                            onClick = onIgnoreDir,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Ignore directory permanently") }
                    } else {
                        OutlinedButton(
                            onClick = onIgnoreDir,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Ignore directory permanently") }
                    }
                }
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
