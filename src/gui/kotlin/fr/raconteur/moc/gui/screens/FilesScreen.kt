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
import fr.raconteur.moc.filesystem.FileDiffKind
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.filesystem.McInstanceRefMocFileSystem
import fr.raconteur.moc.filesystem.applyDiffToDraft
import fr.raconteur.moc.filesystem.directChildren
import fr.raconteur.moc.filesystem.isDescendant
import fr.raconteur.moc.filesystem.openIdeDiff
import fr.raconteur.moc.gui.AppState
import fr.raconteur.moc.gui.Screen
import fr.raconteur.moc.gui.components.*
import fr.raconteur.moc.versioning.DraftPatch
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchMode
import java.nio.file.Path

private fun draftFileEntry(draftEntries: List<PatchEntry>, filePath: Path): PatchEntry? =
    draftEntries.firstOrNull { it.filePath == filePath.toString() && (it.optionPath == "$" || it.optionPath == "") }

private fun hasSubDraft(draftEntries: List<PatchEntry>, filePath: Path, parentPath: String): Boolean =
    draftEntries.any { it.filePath == filePath.toString() && isDescendant(it.optionPath, parentPath) }

private fun applyFileEntry(state: AppState, filePath: Path, mode: PatchMode) {
    val fileDiff = state.entries.find { it.key == filePath }?.value ?: return
    val optDiff = if (fileDiff.kind == FileDiffKind.DELETED)
        fileDiff.flatContentDiff[""] else fileDiff.flatContentDiff["$"]
    optDiff ?: return
    val parentEntry = state.draftEntries.firstOrNull {
        it.filePath == filePath.toString() && isDescendant(optDiff.path, it.optionPath)
    }
    val children = state.draftEntries.filter {
        it.filePath == filePath.toString() && isDescendant(it.optionPath, optDiff.path)
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
        else -> {
            applyDiffToDraft(optDiff, mode)
            state.refreshDraft()
        }
    }
}

@Composable
fun FilesScreen(state: AppState) {
    val entries      = state.entries
    val draftEntries = state.draftEntries

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header / breadcrumb
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FILES", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(12.dp))
            Text("${entries.size} file(s) changed", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.width(12.dp))
            if (draftEntries.isNotEmpty()) {
                DraftBadge("draft: ${draftEntries.size}")
            }
        }
        Spacer(Modifier.height(12.dp))

        // File list
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(entries) { i, (path, fileDiff) ->
                val selected = i == state.fileIndex
                val fileEntry  = draftFileEntry(draftEntries, path)
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
                    Text(
                        text = path.toString(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (draftLabel != null) {
                        Spacer(Modifier.width(8.dp))
                        DraftBadge(draftLabel)
                    }
                    if (fileDiff.flatContentDiff.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text("▶", color = Color.Gray, fontSize = 11.sp)
                    }
                }
                Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 0.5.dp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Action bar
        if (entries.isNotEmpty()) {
            val (filePath, _) = entries[state.fileIndex]
            val fileEntry = draftFileEntry(draftEntries, filePath)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fileEntry == null) {
                    Button(onClick = { applyFileEntry(state, filePath, PatchMode.DEFAULT) })  { Text("D  Default") }
                    Button(onClick = { applyFileEntry(state, filePath, PatchMode.OVERRIDE) }) { Text("O  Override") }
                } else {
                    OutlinedButton(onClick = {
                        DraftPatch.removeEntry(filePath.toString(), fileEntry.optionPath)
                        state.refreshDraft()
                    }) { Text("R  Remove from draft") }
                }
                OutlinedButton(onClick = {
                    val fileDiff = entries[state.fileIndex].value
                    val allPaths = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
                    if (directChildren(allPaths, "$").isNotEmpty()) {
                        state.pathStack = listOf("$"); state.diffIndex = 0
                        state.screen = Screen.Diff
                    } else {
                        state.valuePath = "$"
                        state.screen = Screen.Value(returnTo = Screen.Files)
                    }
                }) { Text("↵  Open") }
                OutlinedButton(onClick = {
                    val (fp, fd) = entries[state.fileIndex]
                    val ext = fp.toString().substringAfterLast('.', "txt")
                    val old = if (fd.kind != FileDiffKind.NEW)
                        McInstanceRefMocFileSystem.files.find { it.relativePath == fp }?.getStringContent() ?: "" else ""
                    val new = if (fd.kind != FileDiffKind.DELETED)
                        McInstanceMocFileSystem.files.find { it.relativePath == fp }?.getStringContent() ?: "" else ""
                    openIdeDiff(old, new, ext)
                }) { Text("I  IDE diff") }
                Spacer(Modifier.weight(1f))
                if (draftEntries.isNotEmpty()) {
                    Button(onClick = { state.draftIndex = 0; state.screen = Screen.Draft })
                    { Text("E  Entries") }
                    Button(onClick = { state.screen = Screen.Finalize })
                    { Text("F  Finalize") }
                }
            }
        }
    }
}
