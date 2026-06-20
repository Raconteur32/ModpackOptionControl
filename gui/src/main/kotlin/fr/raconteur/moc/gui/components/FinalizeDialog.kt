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
import fr.raconteur.moc.gui.AppState
import fr.raconteur.moc.gui.IgnoreStore
import fr.raconteur.moc.gui.Screen
import fr.raconteur.moc.versioning.DraftPatch
import fr.raconteur.moc.versioning.PatchList

@Composable
fun FinalizeDialog(state: AppState) {
    val onDismiss = {
        state.finalizeDialogVisible = false
        state.patchName = ""
        state.patchNameError = null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, elevation = 8.dp) {
            Column(
                modifier = Modifier.padding(32.dp).widthIn(min = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text("FINALIZE PATCH", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${state.draftEntries.size} entr${if (state.draftEntries.size != 1) "ies" else "y"} will be committed.",
                    color = Color.Gray, fontSize = 13.sp
                )
                if (state.lastCreatedPatch != null) {
                    Spacer(Modifier.height(2.dp))
                    Text("Last patch: « ${state.lastCreatedPatch} »", color = Color.Gray, fontSize = 11.sp)
                }
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value         = state.patchName,
                    onValueChange = { name ->
                        state.patchName      = name
                        state.patchNameError = if (name.isNotBlank() && PatchList.contains(name))
                            "« $name » is already taken" else null
                    },
                    label    = { Text("Patch name") },
                    isError  = state.patchNameError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.patchNameError != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(state.patchNameError!!, color = MaterialTheme.colors.error, fontSize = 12.sp)
                }

                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            if (state.patchName.isNotBlank() && state.patchNameError == null) {
                                DraftPatch.finalize(state.patchName)
                                IgnoreStore.resetSession()
                                state.lastCreatedPatch     = state.patchName
                                state.patchName            = ""
                                state.finalizeDialogVisible = false
                                state.refreshDiff()
                                state.refreshDraft()
                                state.refreshIgnore()
                                state.fileIndex = 0
                                state.diffIndex = 0
                                state.pathStack = listOf("$")
                                state.screen    = Screen.Files
                            }
                        },
                        enabled = state.patchName.isNotBlank() && state.patchNameError == null
                    ) { Text("Confirm") }
                }
            }
        }
    }
}
