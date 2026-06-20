package fr.raconteur.moc.gui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun RecompConflictDialog(
    onResume:    () -> Unit,
    onOverwrite: () -> Unit,
    onCancel:    () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = MaterialTheme.shapes.medium, elevation = 8.dp) {
            Column(
                modifier = Modifier.padding(32.dp).widthIn(min = 340.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("RECOMPOSITION IN PROGRESS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "A recomposition draft already exists. What would you like to do?",
                    fontSize = 13.sp
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                        Text("Resume existing recomposition")
                    }
                    OutlinedButton(
                        onClick = onOverwrite,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.error)
                    ) {
                        Text("Overwrite with new range")
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
