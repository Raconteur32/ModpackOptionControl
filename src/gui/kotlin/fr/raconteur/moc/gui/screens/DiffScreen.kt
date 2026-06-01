package fr.raconteur.moc.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.applyDiffToDraft
import fr.raconteur.moc.filesystem.directChildren
import fr.raconteur.moc.filesystem.isDescendant
import fr.raconteur.moc.gui.AppState
import fr.raconteur.moc.gui.Screen
import fr.raconteur.moc.gui.components.DraftBadge
import fr.raconteur.moc.versioning.DraftPatch
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchMode

private fun draftForOption(draftEntries: List<PatchEntry>, filePath: String, optionPath: String): PatchEntry? =
    draftEntries.find { it.filePath == filePath && it.optionPath == optionPath }

private fun hasSubDraft(draftEntries: List<PatchEntry>, filePath: String, parentPath: String): Boolean =
    draftEntries.any { it.filePath == filePath && isDescendant(it.optionPath, parentPath) }

private fun applyOption(state: AppState, filePath: String, selected: String, mode: PatchMode) {
    val fileDiff = state.entries.find { it.key.toString() == filePath }?.value ?: return
    val optDiff  = fileDiff.flatContentDiff[selected] ?: return
    val parentEntry = state.draftEntries.firstOrNull {
        it.filePath == filePath && isDescendant(selected, it.optionPath)
    }
    val children = state.draftEntries.filter {
        it.filePath == filePath && isDescendant(it.optionPath, selected)
    }
    when {
        parentEntry != null -> {
            state.confirmMessage = "Parent entry « ${parentEntry.optionPath} » [${parentEntry.mode}] will be removed."
            state.confirmAction = {
                DraftPatch.removeEntry(parentEntry.filePath, parentEntry.optionPath)
                applyDiffToDraft(optDiff, mode)
                state.refreshDraft()
            }
        }
        children.isNotEmpty() -> {
            state.confirmMessage = "${children.size} sub-entr${if (children.size > 1) "ies" else "y"} will be removed."
            state.confirmAction = {
                children.forEach { DraftPatch.removeEntry(it.filePath, it.optionPath) }
                applyDiffToDraft(optDiff, mode)
                state.refreshDraft()
            }
        }
        else -> { applyDiffToDraft(optDiff, mode); state.refreshDraft() }
    }
}

@Composable
fun DiffScreen(state: AppState) {
    val (filePath, fileDiff) = state.entries[state.fileIndex]
    val filePathStr   = filePath.toString()
    val allPaths      = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
    val currentParent = state.pathStack.last()
    val visible       = directChildren(allPaths, currentParent)
    val draftEntries  = state.draftEntries

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Breadcrumb
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DIFF", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(12.dp))
            Text(filePathStr, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.width(8.dp))
            Text("›", color = Color.Gray)
            Spacer(Modifier.width(8.dp))
            Text(state.pathStack.joinToString(" › "), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))

        if (visible.isEmpty()) {
            Text("(no sub-items)", color = Color.Gray, modifier = Modifier.padding(8.dp))
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(visible) { i, path ->
                    val selected      = i == state.diffIndex
                    val entry         = fileDiff.flatContentDiff[path]
                    val hasChildren   = directChildren(allPaths, path).isNotEmpty()
                    val entryDraft    = draftForOption(draftEntries, filePathStr, path)
                    val hasSub        = hasSubDraft(draftEntries, filePathStr, path)
                    val draftLabel    = when {
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
                            .clickable { state.diffIndex = i }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(prefix, color = color, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(path, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (draftLabel != null) { DraftBadge(draftLabel) }
                        if (hasChildren) {
                            Spacer(Modifier.width(6.dp))
                            Text("▶", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 0.5.dp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Action bar
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.pathStack.size > 1) {
                OutlinedButton(onClick = { state.pathStack = state.pathStack.dropLast(1); state.diffIndex = 0 })
                { Text("↑  Up") }
            } else {
                OutlinedButton(onClick = { state.screen = Screen.Files }) { Text("←  Back") }
            }

            if (visible.isNotEmpty()) {
                val sel       = visible[state.diffIndex]
                val hasChildren = directChildren(allPaths, sel).isNotEmpty()
                val entryDraft  = draftForOption(draftEntries, filePathStr, sel)

                OutlinedButton(onClick = {
                    if (hasChildren) {
                        state.pathStack = state.pathStack + sel; state.diffIndex = 0
                    } else {
                        state.valuePath = sel
                        state.screen = Screen.Value(returnTo = Screen.Diff)
                    }
                }) { Text("↵  Open") }

                if (entryDraft == null) {
                    Button(onClick = { applyOption(state, filePathStr, sel, PatchMode.DEFAULT) })  { Text("D  Default") }
                    Button(onClick = { applyOption(state, filePathStr, sel, PatchMode.OVERRIDE) }) { Text("O  Override") }
                } else {
                    OutlinedButton(onClick = {
                        DraftPatch.removeEntry(filePathStr, sel); state.refreshDraft()
                    }) { Text("R  Remove") }
                }
            }
        }
    }
}
