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
import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.directChildren
import fr.raconteur.moc.filesystem.isDescendant
import fr.raconteur.moc.gui.PatchesState
import fr.raconteur.moc.gui.RecompFocusedPanel
import fr.raconteur.moc.gui.Screen
import fr.raconteur.moc.gui.components.DraftBadge
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchMode

@Composable
fun RecompDiffScreen(state: PatchesState) {
    val entry = state.recompEntries.getOrNull(state.recompFileIndex) ?: return
    val (filePath, fileDiff) = entry
    val filePathStr  = filePath.toString()
    val allPaths     = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
    val visible      = state.recompVisibleDiffItems()
    val draftEntries = state.recompDraftEntries
    val listState    = rememberLazyListState()

    LaunchedEffect(state.recompDiffIndex) {
        if (visible.isNotEmpty()) listState.scrollToItem(state.recompDiffIndex.coerceAtMost(visible.size - 1))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DIFF", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(12.dp))
            Text(filePathStr, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.width(8.dp)); Text("›", color = Color.Gray); Spacer(Modifier.width(8.dp))
            Text(state.recompPathStack.joinToString(" › "), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))

        if (visible.isEmpty()) {
            Text("(no sub-items)", color = Color.Gray, modifier = Modifier.padding(8.dp))
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(visible) { i, path ->
                    val selected    = state.recompFocusedPanel == RecompFocusedPanel.Changes && i == state.recompDiffIndex
                    val entry       = fileDiff.flatContentDiff[path]
                    val hasChildren = directChildren(allPaths, path).isNotEmpty()
                    val entryDraft  = draftEntries.find { it.filePath == filePathStr && it.optionPath == path }
                    val hasSub      = draftEntries.any { it.filePath == filePathStr && isDescendant(it.optionPath, path) }
                    val draftLabel  = when {
                        entryDraft?.mode == PatchMode.OVERRIDE -> "[✓ O]"
                        entryDraft?.mode == PatchMode.DEFAULT  -> "[✓ D]"
                        hasSub                                  -> "[…]"
                        else                                    -> null
                    }
                    val (prefix, color) = when (entry) {
                        is OptionDiff.New     -> "+" to Color(0xFF2E7D32)
                        is OptionDiff.Deleted -> "-" to Color(0xFFC62828)
                        is OptionDiff.Changed -> "~" to Color(0xFFF57F17)
                        null                  -> "?" to Color.Gray
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { state.recompDiffIndex = i }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(prefix, color = color, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(path, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (draftLabel != null) { DraftBadge(draftLabel) }
                        if (hasChildren) { Spacer(Modifier.width(6.dp)); Text("▶", color = Color.Gray, fontSize = 11.sp) }
                    }
                    Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 0.5.dp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.recompPathStack.size > 1) {
                OutlinedButton(onClick = { state.recompGoBack() }) { Text("↑  Up") }
            } else {
                OutlinedButton(onClick = { state.recompScreen = Screen.Files }) { Text("←  Back") }
            }
            if (visible.isNotEmpty()) {
                val entryDraft = state.recompCurrentOptionDraftEntry()
                OutlinedButton(onClick = { state.recompOpenSelected() }) { Text("↵  Open") }
                if (entryDraft == null) {
                    Button(onClick = { state.recompApplyCurrentOption(PatchMode.DEFAULT) })  { Text("D  Default") }
                    Button(onClick = { state.recompApplyCurrentOption(PatchMode.OVERRIDE) }) { Text("O  Override") }
                } else {
                    OutlinedButton(onClick = { state.recompRemoveCurrentOptionDraft() }) { Text("R  Remove") }
                }
            }
        }
    }
}
