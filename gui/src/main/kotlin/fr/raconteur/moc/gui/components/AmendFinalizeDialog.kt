package fr.raconteur.moc.gui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import fr.raconteur.moc.gui.IgnoreStore
import fr.raconteur.moc.gui.PatchesState
import fr.raconteur.moc.versioning.PatchList
import fr.raconteur.moc.versioning.RecompositionDraft

@Composable
fun AmendFinalizeDialog(state: PatchesState) {
    val lastPatchName = PatchList.getAll().lastOrNull()

    val onDismiss = {
        state.recompFinalizeDialogVisible = false
        state.recompPatchName      = ""
        state.recompPatchNameError = null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, elevation = 8.dp) {
            Column(
                modifier = Modifier.padding(32.dp).widthIn(min = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text("AMEND", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${state.recompDraftEntries.size} entr${if (state.recompDraftEntries.size != 1) "ies" else "y"} will replace the last patch.",
                    color = Color.Gray, fontSize = 13.sp
                )
                if (lastPatchName != null) {
                    Spacer(Modifier.height(2.dp))
                    Text("Amending: « $lastPatchName »", color = Color.Gray, fontSize = 11.sp)
                }
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value         = state.recompPatchName,
                    onValueChange = { name ->
                        state.recompPatchName      = name
                        state.recompPatchNameError = if (name.isNotBlank() && PatchList.contains(name) && name != lastPatchName)
                            "« $name » is already taken" else null
                    },
                    label      = { Text("Patch name") },
                    isError    = state.recompPatchNameError != null,
                    singleLine = true,
                    modifier   = Modifier.fillMaxWidth()
                )
                if (state.recompPatchNameError != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(state.recompPatchNameError!!, color = MaterialTheme.colors.error, fontSize = 12.sp)
                }

                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            if (state.recompPatchName.isNotBlank() && state.recompPatchNameError == null) {
                                RecompositionDraft.finalize(state.recompPatchName)
                                IgnoreStore.clearRecompIgnores()
                                state.refreshPatches()
                                state.finishAmend()
                            }
                        },
                        enabled = state.recompPatchName.isNotBlank() && state.recompPatchNameError == null
                    ) { Text("Confirm") }
                }
            }
        }
    }
}
