package fr.raconteur.moc.gui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.raconteur.moc.gui.AppState
import fr.raconteur.moc.gui.Screen
import fr.raconteur.moc.versioning.DraftPatch
import fr.raconteur.moc.versioning.PatchList

@Composable
fun FinalizeScreen(state: AppState) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {

        Text("FINALIZE PATCH", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text("${state.draftEntries.size} entr${if (state.draftEntries.size != 1) "ies" else "y"} will be committed.",
            color = Color.Gray)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.patchName,
            onValueChange = { name ->
                state.patchName = name
                state.patchNameError = if (name.isNotBlank() && PatchList.contains(name))
                    "« $name » is already taken" else null
            },
            label = { Text("Patch name") },
            isError = state.patchNameError != null,
            singleLine = true,
            modifier = Modifier.width(400.dp)
        )

        if (state.patchNameError != null) {
            Spacer(Modifier.height(4.dp))
            Text(state.patchNameError!!, color = MaterialTheme.colors.error, fontSize = 12.sp)
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { state.patchName = ""; state.screen = Screen.Files }) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    if (state.patchName.isNotBlank() && state.patchNameError == null) {
                        DraftPatch.finalize(state.patchName)
                        state.patchName = ""
                        state.refreshDiff()
                        state.refreshDraft()
                        state.fileIndex = 0
                        state.diffIndex = 0
                        state.pathStack = listOf("$")
                        state.screen = Screen.Files
                    }
                },
                enabled = state.patchName.isNotBlank() && state.patchNameError == null
            ) { Text("Confirm") }
        }
    }
}
