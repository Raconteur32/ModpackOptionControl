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
import fr.raconteur.moc.filesystem.FileDiffKind
import fr.raconteur.moc.filesystem.isDescendant
import fr.raconteur.moc.gui.PatchesState
import fr.raconteur.moc.gui.RecompFocusedPanel
import fr.raconteur.moc.gui.Screen
import fr.raconteur.moc.gui.components.DraftBadge
import fr.raconteur.moc.gui.components.KindPrefix
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchMode

private fun recompFileRootEntry(entries: List<PatchEntry>, filePath: String): PatchEntry? =
    entries.firstOrNull { it.filePath == filePath && (it.optionPath == "$" || it.optionPath == "") }

private fun recompHasSubDraft(entries: List<PatchEntry>, filePath: String, parentPath: String): Boolean =
    entries.any { it.filePath == filePath && isDescendant(it.optionPath, parentPath) }

@Composable
fun RecompFilesScreen(state: PatchesState) {
    val entries      = state.recompEntries
    val draftEntries = state.recompDraftEntries
    val listState    = rememberLazyListState()
    val rangeStart   = fr.raconteur.moc.versioning.RecompositionDraft.rangeStart
    val rangeEnd     = fr.raconteur.moc.versioning.RecompositionDraft.rangeEnd
    val allPatches   = fr.raconteur.moc.versioning.PatchList.getAll()

    LaunchedEffect(state.recompFileIndex) {
        if (entries.isNotEmpty())
            listState.scrollToItem(state.recompFileIndex.coerceAtMost(entries.size - 1))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RECOMPOSITION", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.width(12.dp))
            if (rangeStart != null && rangeEnd != null) {
                val names = allPatches.subList(rangeStart, (rangeEnd + 1).coerceAtMost(allPatches.size))
                Text(
                    "patches ${rangeStart + 1}–${rangeEnd + 1}: ${names.joinToString(", ")}",
                    color = Color.Gray, fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("${entries.size} file(s) changed", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))

        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            itemsIndexed(entries) { i, (path, fileDiff) ->
                val selected   = state.recompFocusedPanel == RecompFocusedPanel.Changes && i == state.recompFileIndex
                val fp         = path.toString()
                val fileEntry  = recompFileRootEntry(draftEntries, fp)
                val hasSub     = recompHasSubDraft(draftEntries, fp, "$")
                val draftLabel = when {
                    fileEntry?.mode == PatchMode.OVERRIDE -> "[✓ OVERRIDE]"
                    fileEntry?.mode == PatchMode.DEFAULT  -> "[✓ DEFAULT]"
                    hasSub                                -> "[…]"
                    else                                  -> null
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { state.recompFileIndex = i }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KindPrefix(fileDiff.kind)
                    Spacer(Modifier.width(10.dp))
                    Text(path.toString(), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (draftLabel != null) { Spacer(Modifier.width(8.dp)); DraftBadge(draftLabel) }
                    if (fileDiff.flatContentDiff.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp)); Text("▶", color = Color.Gray, fontSize = 11.sp)
                    }
                }
                Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 0.5.dp)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (entries.isNotEmpty()) {
            val fileEntry = state.recompCurrentFileDraftEntry()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fileEntry == null) {
                    Button(onClick = { state.recompApplyCurrentFile(PatchMode.DEFAULT) })  { Text("D  Default") }
                    Button(onClick = { state.recompApplyCurrentFile(PatchMode.OVERRIDE) }) { Text("O  Override") }
                } else {
                    OutlinedButton(onClick = { state.recompRemoveCurrentFileDraft() }) { Text("R  Remove from draft") }
                }
                OutlinedButton(onClick = { state.recompOpenSelected() }) { Text("↵  Open") }
                Spacer(Modifier.weight(1f))
                if (draftEntries.isNotEmpty()) {
                    Button(onClick = { state.recompFinalizeDialogVisible = true }) { Text("F  Finalize") }
                }
            }
        }
    }
}
