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
import fr.raconteur.moc.gui.PatchesView
import fr.raconteur.moc.versioning.PatchList
import fr.raconteur.moc.versioning.RecompositionDraft

@Composable
fun RecompFinalizeDialog(state: PatchesState) {
    val rangeStart = RecompositionDraft.rangeStart
    val rangeEnd   = RecompositionDraft.rangeEnd
    val allPatches = PatchList.getAll()
    val rangeNames = if (rangeStart != null && rangeEnd != null)
        allPatches.subList(rangeStart, (rangeEnd + 1).coerceAtMost(allPatches.size))
    else emptyList()

    val onDismiss = {
        state.recompFinalizeDialogVisible = false
        state.recompPatchName      = ""
        state.recompPatchNameError = null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, elevation = 8.dp) {
            Column(
                modifier = Modifier.padding(32.dp).widthIn(min = 380.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text("FINALIZE RECOMPOSITION", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${state.recompDraftEntries.size} entr${if (state.recompDraftEntries.size != 1) "ies" else "y"} will replace ${rangeNames.size} patch${if (rangeNames.size != 1) "es" else ""}.",
                    color = Color.Gray, fontSize = 13.sp
                )
                if (rangeNames.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Replacing: ${rangeNames.joinToString(", ")}",
                        color = Color.Gray, fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value         = state.recompPatchName,
                    onValueChange = { name ->
                        state.recompPatchName      = name
                        state.recompPatchNameError = if (name.isNotBlank() && PatchList.contains(name))
                            "« $name » is already taken" else null
                    },
                    label      = { Text("New patch name") },
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
                                state.recompFinalizeDialogVisible = false
                                state.recompPatchName      = ""
                                state.recompPatchNameError = null
                                state.refreshPatches()
                                state.view = PatchesView.List
                            }
                        },
                        enabled = state.recompPatchName.isNotBlank() && state.recompPatchNameError == null
                    ) { Text("Confirm") }
                }
            }
        }
    }
}
