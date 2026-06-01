package fr.raconteur.moc.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.filesystem.McInstanceRefMocFileSystem
import fr.raconteur.moc.versioning.DraftPatch
import fr.raconteur.moc.versioning.PatchEntry
import java.nio.file.Path

sealed class Screen {
    object Files   : Screen()
    object Diff    : Screen()
    data class Value(val returnTo: Screen) : Screen()
    object Draft   : Screen()
    object Finalize: Screen()
}

class AppState {
    var entries      by mutableStateOf(loadDiff())
    var draftEntries by mutableStateOf<List<PatchEntry>>(DraftPatch.entries.toList())
    var screen       by mutableStateOf<Screen>(Screen.Files)
    var fileIndex    by mutableStateOf(0)
    var diffIndex    by mutableStateOf(0)
    var draftIndex   by mutableStateOf(0)
    var pathStack    by mutableStateOf(listOf("$"))
    var valuePath    by mutableStateOf<String?>(null)
    var patchName    by mutableStateOf("")
    var patchNameError by mutableStateOf<String?>(null)
    var confirmMessage by mutableStateOf<String?>(null)
    var confirmAction  by mutableStateOf<(() -> Unit)?>(null)

    fun refreshDiff() {
        entries = loadDiff()
        fileIndex = fileIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    }

    fun refreshDraft() {
        draftEntries = DraftPatch.entries.toList()
        draftIndex = draftIndex.coerceIn(0, (draftEntries.size - 1).coerceAtLeast(0))
    }

    fun currentFilePath(): Path? = entries.getOrNull(fileIndex)?.key

    private fun loadDiff() =
        McInstanceMocFileSystem.diffFrom(McInstanceRefMocFileSystem).entries.sortedBy { it.key.toString() }
}
