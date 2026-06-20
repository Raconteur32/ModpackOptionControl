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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.raconteur.moc.gui.PatchesState
import fr.raconteur.moc.gui.RecompFocusedPanel
import fr.raconteur.moc.gui.components.DraftBadge
import fr.raconteur.moc.versioning.RecompositionDraft
import fr.raconteur.moc.versioning.PatchMode

@Composable
fun RecompDraftPanel(state: PatchesState) {
    val draftEntries = state.recompDraftEntries
    val listState    = rememberLazyListState()

    LaunchedEffect(state.recompDraftIndex) {
        if (draftEntries.isNotEmpty())
            listState.scrollToItem(state.recompDraftIndex.coerceAtMost(draftEntries.size - 1))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RECOMP DRAFT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "${draftEntries.size} entr${if (draftEntries.size != 1) "ies" else "y"}",
                color = Color.Gray, fontSize = 11.sp
            )
            Spacer(Modifier.weight(1f))
            if (draftEntries.isNotEmpty()) {
                Button(
                    onClick = { state.recompFinalizeDialogVisible = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("F  Finalize", fontSize = 11.sp) }
            }
        }
        Divider(color = Color.Gray.copy(alpha = 0.20f), thickness = 1.dp)

        if (draftEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No entries selected.", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(draftEntries) { i, entry ->
                    val selected  = state.recompFocusedPanel == RecompFocusedPanel.Draft && i == state.recompDraftIndex
                    val modeLabel = if (entry.mode == PatchMode.OVERRIDE) "[O]" else "[D]"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) MaterialTheme.colors.primary.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable { state.recompDraftIndex = i }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(entry.filePath) }
                                append("  ")
                                withStyle(SpanStyle(color = Color.Gray, fontSize = 10.sp)) { append(entry.optionPath) }
                            },
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        DraftBadge(modeLabel)
                        Spacer(Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = {
                                RecompositionDraft.removeEntry(entry.filePath, entry.optionPath)
                                state.refreshRecompDraft()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("R", fontSize = 11.sp) }
                    }
                    Divider(color = Color.Gray.copy(alpha = 0.12f), thickness = 0.5.dp)
                }
            }
        }
    }
}
