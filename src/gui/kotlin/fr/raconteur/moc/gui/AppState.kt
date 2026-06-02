package fr.raconteur.moc.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.raconteur.moc.filesystem.FileDiffKind
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.filesystem.McInstanceRefMocFileSystem
import fr.raconteur.moc.filesystem.applyDiffToDraft
import fr.raconteur.moc.filesystem.directChildren
import fr.raconteur.moc.filesystem.isDescendant
import fr.raconteur.moc.versioning.DraftPatch
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchMode
import java.nio.file.Path

sealed class Screen {
    object Files   : Screen()
    object Diff    : Screen()
    data class Value(val returnTo: Screen) : Screen()
    object Draft   : Screen()
    object Finalize: Screen()
}

enum class AppTab(val label: String) {
    Changes("Changes"),
    Draft("Draft")
}

private fun tabOf(s: Screen): AppTab = when (s) {
    is Screen.Files, is Screen.Diff, is Screen.Value -> AppTab.Changes
    is Screen.Draft, is Screen.Finalize              -> AppTab.Draft
}

class AppState {
    var entries      by mutableStateOf(loadDiff())
    var draftEntries by mutableStateOf<List<PatchEntry>>(DraftPatch.entries.toList())

    var currentTab by mutableStateOf(AppTab.Changes)
        private set

    private var savedChangesScreen: Screen = Screen.Files
    private var savedDraftScreen: Screen   = Screen.Draft

    private var _screen by mutableStateOf<Screen>(Screen.Files)
    var screen: Screen
        get() = _screen
        set(value) {
            val newTab = tabOf(value)
            if (newTab != currentTab) {
                saveCurrentTabScreen()
                currentTab = newTab
            }
            _screen = value
        }

    private fun saveCurrentTabScreen() {
        when (currentTab) {
            AppTab.Changes -> savedChangesScreen = _screen
            AppTab.Draft   -> savedDraftScreen   = _screen
        }
    }

    fun switchTab(tab: AppTab) {
        if (tab == currentTab) return
        saveCurrentTabScreen()
        currentTab = tab
        _screen = when (tab) {
            AppTab.Changes -> savedChangesScreen
            AppTab.Draft   -> savedDraftScreen
        }
    }

    fun switchToNextTab() {
        val tabs = AppTab.entries
        switchTab(tabs[(currentTab.ordinal + 1) % tabs.size])
    }

    var fileIndex    by mutableStateOf(0)
    var diffIndex    by mutableStateOf(0)
    var draftIndex   by mutableStateOf(0)
    var pathStack    by mutableStateOf(listOf("$"))
    var valuePath    by mutableStateOf<String?>(null)
    var patchName    by mutableStateOf("")
    var patchNameError by mutableStateOf<String?>(null)
    var confirmMessage by mutableStateOf<String?>(null)
    var confirmAction  by mutableStateOf<(() -> Unit)?>(null)
    var valueRawMode   by mutableStateOf(true)

    fun refreshDiff() {
        entries = loadDiff()
        fileIndex = fileIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    }

    fun refreshDraft() {
        draftEntries = DraftPatch.entries.toList()
        draftIndex = draftIndex.coerceIn(0, (draftEntries.size - 1).coerceAtLeast(0))
    }

    fun currentFilePath(): Path? = entries.getOrNull(fileIndex)?.key

    // ── Navigation ────────────────────────────────────────────────────────

    fun moveUp() {
        if (confirmMessage != null) return
        when (screen) {
            is Screen.Files -> if (fileIndex > 0) fileIndex--
            is Screen.Diff  -> if (diffIndex > 0) diffIndex--
            is Screen.Draft -> if (draftIndex > 0) draftIndex--
            else -> {}
        }
    }

    fun moveDown() {
        if (confirmMessage != null) return
        when (screen) {
            is Screen.Files -> if (fileIndex < entries.size - 1) fileIndex++
            is Screen.Diff  -> { val n = visibleDiffItems().size; if (diffIndex < n - 1) diffIndex++ }
            is Screen.Draft -> if (draftIndex < draftEntries.size - 1) draftIndex++
            else -> {}
        }
    }

    fun openSelected() {
        if (confirmMessage != null) return
        when (screen) {
            is Screen.Files -> navigateIntoFile()
            is Screen.Diff  -> navigateIntoDiff()
            else -> {}
        }
    }

    fun goBack() {
        if (confirmMessage != null) return
        when (val s = screen) {
            is Screen.Diff     -> if (pathStack.size > 1) { pathStack = pathStack.dropLast(1); diffIndex = 0 }
                                  else screen = Screen.Files
            is Screen.Value    -> screen = s.returnTo
            is Screen.Draft    -> screen = Screen.Files
            is Screen.Finalize -> { patchName = ""; screen = Screen.Files }
            else -> {}
        }
    }

    // ── File-level actions (FilesScreen) ──────────────────────────────────

    fun currentFileDraftEntry(): PatchEntry? {
        val fp = entries.getOrNull(fileIndex)?.key?.toString() ?: return null
        return draftEntries.firstOrNull { it.filePath == fp && (it.optionPath == "$" || it.optionPath == "") }
    }

    fun applyCurrentFile(mode: PatchMode) {
        if (confirmMessage != null) return
        val filePath = entries.getOrNull(fileIndex)?.key ?: return
        val fileDiff = entries.getOrNull(fileIndex)?.value ?: return
        val optDiff  = if (fileDiff.kind == FileDiffKind.DELETED)
            fileDiff.flatContentDiff[""] else fileDiff.flatContentDiff["$"]
        optDiff ?: return
        val fpStr       = filePath.toString()
        val parentEntry = draftEntries.firstOrNull { it.filePath == fpStr && isDescendant(optDiff.path, it.optionPath) }
        val children    = draftEntries.filter    { it.filePath == fpStr && isDescendant(it.optionPath, optDiff.path) }
        when {
            parentEntry != null -> {
                confirmMessage = "Parent entry « ${parentEntry.optionPath} » [${parentEntry.mode}] will be removed."
                confirmAction  = { DraftPatch.removeEntry(parentEntry.filePath, parentEntry.optionPath); applyDiffToDraft(optDiff, mode); refreshDraft() }
            }
            children.isNotEmpty() -> {
                confirmMessage = "${children.size} sub-entr${if (children.size > 1) "ies" else "y"} will be removed."
                confirmAction  = { children.forEach { DraftPatch.removeEntry(it.filePath, it.optionPath) }; applyDiffToDraft(optDiff, mode); refreshDraft() }
            }
            else -> { applyDiffToDraft(optDiff, mode); refreshDraft() }
        }
    }

    fun removeCurrentFileDraft() {
        if (confirmMessage != null) return
        val entry = currentFileDraftEntry() ?: return
        DraftPatch.removeEntry(entry.filePath, entry.optionPath)
        refreshDraft()
    }

    // ── Option-level actions (DiffScreen) ─────────────────────────────────

    fun visibleDiffItems(): List<String> {
        val fileDiff = entries.getOrNull(fileIndex)?.value ?: return emptyList()
        val allPaths = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
        return directChildren(allPaths, pathStack.last())
    }

    fun currentOptionDraftEntry(): PatchEntry? {
        val fp      = entries.getOrNull(fileIndex)?.key?.toString() ?: return null
        val visible = visibleDiffItems()
        if (visible.isEmpty()) return null
        return draftEntries.find { it.filePath == fp && it.optionPath == visible[diffIndex] }
    }

    fun applyCurrentOption(mode: PatchMode) {
        if (confirmMessage != null) return
        val filePath = entries.getOrNull(fileIndex)?.key?.toString() ?: return
        val visible  = visibleDiffItems()
        if (visible.isEmpty()) return
        val selected = visible[diffIndex]
        val fileDiff = entries.getOrNull(fileIndex)?.value ?: return
        val optDiff  = fileDiff.flatContentDiff[selected] ?: return
        val parentEntry = draftEntries.firstOrNull { it.filePath == filePath && isDescendant(selected, it.optionPath) }
        val children    = draftEntries.filter    { it.filePath == filePath && isDescendant(it.optionPath, selected) }
        when {
            parentEntry != null -> {
                confirmMessage = "Parent entry « ${parentEntry.optionPath} » [${parentEntry.mode}] will be removed."
                confirmAction  = { DraftPatch.removeEntry(parentEntry.filePath, parentEntry.optionPath); applyDiffToDraft(optDiff, mode); refreshDraft() }
            }
            children.isNotEmpty() -> {
                confirmMessage = "${children.size} sub-entr${if (children.size > 1) "ies" else "y"} will be removed."
                confirmAction  = { children.forEach { DraftPatch.removeEntry(it.filePath, it.optionPath) }; applyDiffToDraft(optDiff, mode); refreshDraft() }
            }
            else -> { applyDiffToDraft(optDiff, mode); refreshDraft() }
        }
    }

    fun removeCurrentOptionDraft() {
        if (confirmMessage != null) return
        val filePath = entries.getOrNull(fileIndex)?.key?.toString() ?: return
        val visible  = visibleDiffItems()
        if (visible.isEmpty()) return
        DraftPatch.removeEntry(filePath, visible[diffIndex])
        refreshDraft()
    }

    // ── Value-level actions (ValueScreen) ─────────────────────────────────

    fun applyCurrentValue(mode: PatchMode) {
        if (confirmMessage != null) return
        val filePath = entries.getOrNull(fileIndex)?.key?.toString() ?: return
        val vp       = valuePath ?: return
        val fileDiff = entries.getOrNull(fileIndex)?.value ?: return
        val optDiff  = fileDiff.flatContentDiff[vp] ?: return
        val parentEntry = draftEntries.firstOrNull { it.filePath == filePath && isDescendant(vp, it.optionPath) }
        val children    = draftEntries.filter    { it.filePath == filePath && isDescendant(it.optionPath, vp) }
        when {
            parentEntry != null -> {
                confirmMessage = "Parent entry « ${parentEntry.optionPath} » [${parentEntry.mode}] will be removed."
                confirmAction  = { DraftPatch.removeEntry(parentEntry.filePath, parentEntry.optionPath); applyDiffToDraft(optDiff, mode); refreshDraft() }
            }
            children.isNotEmpty() -> {
                confirmMessage = "${children.size} sub-entr${if (children.size > 1) "ies" else "y"} will be removed."
                confirmAction  = { children.forEach { DraftPatch.removeEntry(it.filePath, it.optionPath) }; applyDiffToDraft(optDiff, mode); refreshDraft() }
            }
            else -> { applyDiffToDraft(optDiff, mode); refreshDraft() }
        }
    }

    fun removeCurrentValueDraft() {
        if (confirmMessage != null) return
        val fp = entries.getOrNull(fileIndex)?.key?.toString() ?: return
        val vp = valuePath ?: return
        DraftPatch.removeEntry(fp, vp)
        refreshDraft()
    }

    // ── Draft-level actions (DraftScreen) ─────────────────────────────────

    fun removeCurrentDraftEntry() {
        if (confirmMessage != null) return
        val entry = draftEntries.getOrNull(draftIndex) ?: return
        DraftPatch.removeEntry(entry.filePath, entry.optionPath)
        refreshDraft()
    }

    // ── Private ───────────────────────────────────────────────────────────

    private fun navigateIntoFile() {
        val fileDiff = entries.getOrNull(fileIndex)?.value ?: return
        val allPaths = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
        if (directChildren(allPaths, "$").isNotEmpty()) {
            pathStack = listOf("$"); diffIndex = 0; screen = Screen.Diff
        } else {
            valuePath = "$"; screen = Screen.Value(returnTo = Screen.Files)
        }
    }

    private fun navigateIntoDiff() {
        val visible  = visibleDiffItems()
        if (visible.isEmpty()) return
        val sel      = visible[diffIndex]
        val fileDiff = entries.getOrNull(fileIndex)?.value ?: return
        val allPaths = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
        if (directChildren(allPaths, sel).isNotEmpty()) {
            pathStack = pathStack + sel; diffIndex = 0
        } else {
            valuePath = sel; screen = Screen.Value(returnTo = Screen.Diff)
        }
    }

    private fun loadDiff() =
        McInstanceMocFileSystem.diffFrom(McInstanceRefMocFileSystem).entries.sortedBy { it.key.toString() }
}
