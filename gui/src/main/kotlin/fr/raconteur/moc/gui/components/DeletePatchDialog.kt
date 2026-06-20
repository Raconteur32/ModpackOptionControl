package fr.raconteur.moc.gui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun DeletePatchDialog(
    patchName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, elevation = 8.dp) {
            Column(
                modifier = Modifier.padding(32.dp).widthIn(min = 320.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("DELETE PATCH", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    color = MaterialTheme.colors.error)
                Text(
                    "« $patchName »",
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    "This patch will be removed from the patch list and its folder will be deleted. " +
                    "It will be added to the deleted-patch list so it is cleaned up for all users.",
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                    ) { Text("Delete", color = MaterialTheme.colors.onError) }
                }
            }
        }
    }
}
