package fr.raconteur.moc.gui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import fr.raconteur.moc.filesystem.isDescendant
import fr.raconteur.moc.filesystem.openIdeDiff
import fr.raconteur.moc.gui.AppState
import fr.raconteur.moc.gui.Screen
import fr.raconteur.moc.gui.components.DraftBadge
import fr.raconteur.moc.versioning.DraftPatch
import fr.raconteur.moc.versioning.PatchMode

private fun preview(v: Any?) = v?.toString()?.replace("\n", "↵")
    ?.take(2000) ?: "null"

@Composable
fun ValueScreen(state: AppState, returnTo: Screen) {
    val (filePath, fileDiff) = state.entries[state.fileIndex]
    val vp      = state.valuePath ?: return
    val optDiff = fileDiff.flatContentDiff[vp]
    val inDraft = state.draftEntries.find { it.filePath == filePath.toString() && it.optionPath == vp }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Breadcrumb
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VALUE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(12.dp))
            Text(filePath.toString(), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.width(8.dp))
            Text("›", color = Color.Gray)
            Spacer(Modifier.width(8.dp))
            Text(vp, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            if (inDraft != null) {
                Spacer(Modifier.width(10.dp))
                DraftBadge("[✓ ${inDraft.mode}]")
            }
        }
        Spacer(Modifier.height(16.dp))

        // Diff display
        when (optDiff) {
            is OptionDiff.New -> {
                Text("+ New", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
                    Text(
                        text = preview(optDiff.newValue),
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
            is OptionDiff.Deleted -> {
                Text("- Deleted", color = Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp,
                    backgroundColor = Color(0xFFFFF3F3)) {
                    Text(
                        text = preview(optDiff.oldValue),
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFC62828),
                        modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
            is OptionDiff.Changed -> {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Before", color = Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Card(modifier = Modifier.fillMaxSize(), elevation = 2.dp,
                            backgroundColor = Color(0xFFFFF3F3)) {
                            Text(
                                text = preview(optDiff.oldValue),
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFC62828),
                                modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("After", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Card(modifier = Modifier.fillMaxSize(), elevation = 2.dp,
                            backgroundColor = Color(0xFFF3FFF3)) {
                            Text(
                                text = preview(optDiff.newValue),
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
            null -> Text("(unknown value)", color = Color.Gray)
        }

        Spacer(Modifier.height(12.dp))

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { state.screen = returnTo }) { Text("←  Back") }

            if (optDiff is OptionDiff.Changed) {
                OutlinedButton(onClick = {
                    val ext = filePath.toString().substringAfterLast('.', "txt")
                    openIdeDiff(optDiff.oldValue, optDiff.newValue, ext)
                }) { Text("I  IDE diff") }
            }

            Spacer(Modifier.weight(1f))

            if (inDraft != null) {
                OutlinedButton(onClick = {
                    DraftPatch.removeEntry(filePath.toString(), vp); state.refreshDraft()
                }) { Text("R  Remove from draft") }
            } else if (optDiff != null) {
                val parentEntry = state.draftEntries.firstOrNull {
                    it.filePath == filePath.toString() && isDescendant(vp, it.optionPath)
                }
                val children = state.draftEntries.filter {
                    it.filePath == filePath.toString() && isDescendant(it.optionPath, vp)
                }
                fun doApply(mode: PatchMode) {
                    when {
                        parentEntry != null -> {
                            state.confirmMessage = "Parent entry « ${parentEntry.optionPath} » [${parentEntry.mode}] will be removed."
                            state.confirmAction = {
                                DraftPatch.removeEntry(parentEntry.filePath, parentEntry.optionPath)
                                applyDiffToDraft(optDiff, mode); state.refreshDraft()
                            }
                        }
                        children.isNotEmpty() -> {
                            state.confirmMessage = "${children.size} sub-entr${if (children.size > 1) "ies" else "y"} will be removed."
                            state.confirmAction = {
                                children.forEach { DraftPatch.removeEntry(it.filePath, it.optionPath) }
                                applyDiffToDraft(optDiff, mode); state.refreshDraft()
                            }
                        }
                        else -> { applyDiffToDraft(optDiff, mode); state.refreshDraft() }
                    }
                }
                Button(onClick = { doApply(PatchMode.DEFAULT) })  { Text("D  Default") }
                Button(onClick = { doApply(PatchMode.OVERRIDE) }) { Text("O  Override") }
            }
        }
    }
}
