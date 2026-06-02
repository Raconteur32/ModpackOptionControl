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
import fr.raconteur.moc.filesystem.isDescendant
import fr.raconteur.moc.gui.AppState
import fr.raconteur.moc.gui.Screen
import fr.raconteur.moc.gui.components.*
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchMode
import java.nio.file.Path

private fun fileRootDraftEntry(draftEntries: List<PatchEntry>, filePath: Path): PatchEntry? =
    draftEntries.firstOrNull { it.filePath == filePath.toString() && (it.optionPath == "$" || it.optionPath == "") }

private fun hasSubDraft(draftEntries: List<PatchEntry>, filePath: Path, parentPath: String): Boolean =
    draftEntries.any { it.filePath == filePath.toString() && isDescendant(it.optionPath, parentPath) }

@Composable
fun FilesScreen(state: AppState) {
    val entries      = state.entries
    val draftEntries = state.draftEntries
    val listState    = rememberLazyListState()

    LaunchedEffect(state.fileIndex) { listState.scrollToItem(state.fileIndex) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FILES", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(12.dp))
            Text("${entries.size} file(s) changed", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.width(12.dp))
            if (draftEntries.isNotEmpty()) DraftBadge("draft: ${draftEntries.size}")
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            itemsIndexed(entries) { i, (path, fileDiff) ->
                val selected   = i == state.fileIndex
                val fileEntry  = fileRootDraftEntry(draftEntries, path)
                val hasSub     = hasSubDraft(draftEntries, path, "$")
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
                        .clickable { state.fileIndex = i }
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
            val fileEntry = state.currentFileDraftEntry()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fileEntry == null) {
                    Button(onClick = { state.applyCurrentFile(PatchMode.DEFAULT) })  { Text("D  Default") }
                    Button(onClick = { state.applyCurrentFile(PatchMode.OVERRIDE) }) { Text("O  Override") }
                } else {
                    OutlinedButton(onClick = { state.removeCurrentFileDraft() }) { Text("R  Remove from draft") }
                }
                OutlinedButton(onClick = { state.openSelected() }) { Text("↵  Open") }
                Spacer(Modifier.weight(1f))
                if (draftEntries.isNotEmpty()) {
                    Button(onClick = { state.draftIndex = 0; state.screen = Screen.Draft }) { Text("E  Entries") }
                    Button(onClick = { state.screen = Screen.Finalize })                    { Text("F  Finalize") }
                }
            }
        }
    }
}
