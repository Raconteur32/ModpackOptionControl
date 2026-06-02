package fr.raconteur.moc.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.raconteur.moc.gui.AppState
import fr.raconteur.moc.gui.Screen
import fr.raconteur.moc.gui.components.DraftBadge
import fr.raconteur.moc.versioning.DraftPatch
import fr.raconteur.moc.versioning.PatchMode

@Composable
fun DraftScreen(state: AppState) {
    val draftEntries = state.draftEntries
    val listState    = rememberLazyListState()

    LaunchedEffect(state.draftIndex) { listState.scrollToItem(state.draftIndex) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DRAFT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(12.dp))
            Text("${draftEntries.size} pending entr${if (draftEntries.size != 1) "ies" else "y"}",
                color = Color.Gray, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))

        if (draftEntries.isEmpty()) {
            Text("No pending entries.", color = Color.Gray, modifier = Modifier.padding(8.dp))
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(draftEntries) { i, entry ->
                    val selected  = i == state.draftIndex
                    val modeLabel = if (entry.mode == PatchMode.OVERRIDE) "[✓ OVERRIDE]" else "[✓ DEFAULT]"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { state.draftIndex = i }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.filePath, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                                fontWeight = FontWeight.Medium)
                            Text(entry.optionPath, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                color = Color.Gray)
                        }
                        DraftBadge(modeLabel)
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                DraftPatch.removeEntry(entry.filePath, entry.optionPath)
                                state.refreshDraft()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("R  Remove", fontSize = 12.sp) }
                    }
                    Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 0.5.dp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.screen = Screen.Files }) { Text("←  Back") }
            Spacer(Modifier.weight(1f))
            if (draftEntries.isNotEmpty()) {
                Button(onClick = { state.screen = Screen.Finalize }) { Text("F  Finalize patch") }
            }
        }
    }
}
