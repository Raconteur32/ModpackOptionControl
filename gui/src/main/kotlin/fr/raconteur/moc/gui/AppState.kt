package fr.raconteur.moc.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.raconteur.moc.MocSettings
import fr.raconteur.moc.filesystem.FileDiffKind
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.filesystem.McInstanceRefMocFileSystem
import fr.raconteur.moc.filesystem.MocFileDiff
import fr.raconteur.moc.filesystem.applyDiffToDraft
import fr.raconteur.moc.filesystem.directChildren
import fr.raconteur.moc.filesystem.isDescendant
import fr.raconteur.moc.versioning.DraftPatch
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchList
import fr.raconteur.moc.versioning.PatchMode
import java.nio.file.Path

sealed class Screen {
    object Files : Screen()
    object Diff  : Screen()
    data class Value(val returnTo: Screen) : Screen()
}

enum class IgnoreFilter(val label: String) {
    All("All"),
    Session("Until next patch"),
    Value("Until value change"),
    Permanent("Permanent"),
    Directory("Directory ignore")
}

enum class FocusedPanel { Changes, Draft, Ignores }

class AppState {
    var entries      by mutableStateOf(loadDiff())
    var draftEntries by mutableStateOf<List<PatchEntry>>(DraftPatch.entries.toList())

    var ignoreSessionEntries   by mutableStateOf(IgnoreStore.sessionIgnores)
    var ignoreValueEntries     by mutableStateOf(IgnoreStore.valueIgnores)
    var ignorePermanentEntries by mutableStateOf(IgnoreStore.permanentIgnores)
    var ignoredDirectories     by mutableStateOf(MocSettings.ignoredPaths.toList())

    var screen by mutableStateOf<Screen>(Screen.Files)

    var fileIndex   by mutableStateOf(0)
    var diffIndex   by mutableStateOf(0)
    var draftIndex  by mutableStateOf(0)
    var ignoreIndex by mutableStateOf(0)
    var pathStack   by mutableStateOf(listOf("$"))

    private var _ignoreSearch by mutableStateOf("")
    var ignoreSearch: String
        get() = _ignoreSearch
        set(value) { _ignoreSearch = value; ignoreIndex = 0 }

    var ignoreSearchFocused: Boolean = false
    var clearFocusGeneration by mutableStateOf(0)
        private set

    fun requestClearFocus() { clearFocusGeneration++ }

    var ignoreFilter  by mutableStateOf(IgnoreFilter.All)
    var focusedPanel  by mutableStateOf(FocusedPanel.Changes)

    var valuePath      by mutableStateOf<String?>(null)
    var patchName      by mutableStateOf("")
    var patchNameError by mutableStateOf<String?>(null)
    var lastCreatedPatch by mutableStateOf(PatchList.getAll().lastOrNull())

    var confirmMessage by mutableStateOf<String?>(null)
    var confirmAction  by mutableStateOf<(() -> Unit)?>(null)

    var valueRawMode by mutableStateOf(true)

    var ignoreDialogVisible    by mutableStateOf(false)
    var ignoreDialogSelection  by mutableStateOf(0)
    var ignoreDirDialogVisible by mutableStateOf(false)
    var ignoreDirDialogPath    by mutableStateOf("")
    var finalizeDialogVisible  by mutableStateOf(false)

    val ignoreDialogIsFile: Boolean get() = screen is Screen.Files

    fun refreshDiff() {
        entries = loadDiff()
        refreshIgnore()
        fileIndex = fileIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        diffIndex = diffIndex.coerceIn(0, (visibleDiffItems().size - 1).coerceAtLeast(0))
    }

    fun refreshDraft() {
        draftEntries = DraftPatch.entries.toList()
        draftIndex = draftIndex.coerceIn(0, (draftEntries.size - 1).coerceAtLeast(0))
    }

    fun refreshIgnore() {
        ignoreSessionEntries   = IgnoreStore.sessionIgnores
        ignoreValueEntries     = IgnoreStore.valueIgnores
        ignorePermanentEntries = IgnoreStore.permanentIgnores
        ignoredDirectories     = MocSettings.ignoredPaths.toList()
        val n = if (ignoreFilter == IgnoreFilter.Directory) ignoredDirectories.size
                else currentIgnoreEntriesWithKind().size
        ignoreIndex = ignoreIndex.coerceIn(0, (n - 1).coerceAtLeast(0))
    }

    fun currentIgnoreEntriesWithKind(): List<Pair<IgnoreEntry, IgnoreKind>> {
        val raw: List<Pair<IgnoreEntry, IgnoreKind>> = when (ignoreFilter) {
            IgnoreFilter.All ->
                ignoreSessionEntries.map   { it to IgnoreKind.Session } +
                ignoreValueEntries.map     { it to IgnoreKind.Value } +
                ignorePermanentEntries.map { it to IgnoreKind.Permanent } +
                ignoredDirectories.map     { IgnoreEntry(it.toString(), "", null) to IgnoreKind.Directory }
            IgnoreFilter.Session   -> ignoreSessionEntries.map   { it to IgnoreKind.Session }
            IgnoreFilter.Value     -> ignoreValueEntries.map     { it to IgnoreKind.Value }
            IgnoreFilter.Permanent -> ignorePermanentEntries.map { it to IgnoreKind.Permanent }
            IgnoreFilter.Directory -> return emptyList()
        }
        if (_ignoreSearch.isBlank()) return raw
        val q = _ignoreSearch.lowercase()
        return raw.filter { (entry, _) ->
            entry.filePath.lowercase().contains(q) || entry.optionPath.lowercase().contains(q)
        }
    }

    fun currentFilePath(): Path? = entries.getOrNull(fileIndex)?.key

    // ── Focus ─────────────────────────────────────────────────────────────

    fun switchFocusNext() {
        focusedPanel = when (focusedPanel) {
            FocusedPanel.Changes -> FocusedPanel.Draft
            FocusedPanel.Draft   -> FocusedPanel.Ignores
            FocusedPanel.Ignores -> FocusedPanel.Changes
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────

    fun moveUp() {
        if (confirmMessage != null) return
        when (focusedPanel) {
            FocusedPanel.Changes -> when (screen) {
                is Screen.Files -> if (fileIndex > 0) fileIndex--
                is Screen.Diff  -> if (diffIndex > 0) diffIndex--
                else -> {}
            }
            FocusedPanel.Draft   -> if (draftIndex > 0) draftIndex--
            FocusedPanel.Ignores -> if (ignoreIndex > 0) ignoreIndex--
        }
    }

    fun moveDown() {
        if (confirmMessage != null) return
        when (focusedPanel) {
            FocusedPanel.Changes -> when (screen) {
                is Screen.Files -> if (fileIndex < entries.size - 1) fileIndex++
                is Screen.Diff  -> { val n = visibleDiffItems().size; if (diffIndex < n - 1) diffIndex++ }
                else -> {}
            }
            FocusedPanel.Draft   -> if (draftIndex < draftEntries.size - 1) draftIndex++
            FocusedPanel.Ignores -> {
                val max = if (ignoreFilter == IgnoreFilter.Directory) ignoredDirectories.size
                          else currentIgnoreEntriesWithKind().size
                if (ignoreIndex < max - 1) ignoreIndex++
            }
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
            is Screen.Diff  -> if (pathStack.size > 1) { pathStack = pathStack.dropLast(1); diffIndex = 0 }
                               else screen = Screen.Files
            is Screen.Value -> screen = s.returnTo
            else -> {}
        }
    }

    // ── File-level actions ────────────────────────────────────────────────

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

    // ── Option-level actions ──────────────────────────────────────────────

    fun visibleDiffItems(): List<String> {
        val fileDiff        = entries.getOrNull(fileIndex)?.value ?: return emptyList()
        val fp              = entries.getOrNull(fileIndex)?.key?.toString() ?: return emptyList()
        val allNonRootPaths = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
        return directChildren(allNonRootPaths, pathStack.last()).filter { path ->
            !isEffectivelyHidden(fp, path, fileDiff, allNonRootPaths)
        }
    }

    private fun isEffectivelyHidden(fp: String, path: String, fileDiff: MocFileDiff, allNonRootPaths: List<String>): Boolean {
        if (IgnoreStore.isIgnored(fp, path, fileDiff.flatContentDiff[path]?.newValue)) return true
        val children = directChildren(allNonRootPaths, path)
        if (children.isEmpty()) return false
        return children.all { isEffectivelyHidden(fp, it, fileDiff, allNonRootPaths) }
    }

    fun currentOptionDraftEntry(): PatchEntry? {
        val fp      = entries.getOrNull(fileIndex)?.key?.toString() ?: return null
        val visible = visibleDiffItems()
        if (diffIndex >= visible.size) return null
        return draftEntries.find { it.filePath == fp && it.optionPath == visible[diffIndex] }
    }

    fun applyCurrentOption(mode: PatchMode) {
        if (confirmMessage != null) return
        val filePath = entries.getOrNull(fileIndex)?.key?.toString() ?: return
        val visible  = visibleDiffItems()
        if (diffIndex >= visible.size) return
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
        if (diffIndex >= visible.size) return
        DraftPatch.removeEntry(filePath, visible[diffIndex])
        refreshDraft()
    }

    // ── Value-level actions ───────────────────────────────────────────────

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

    // ── Draft-level actions ───────────────────────────────────────────────

    fun removeCurrentDraftEntry() {
        if (confirmMessage != null) return
        val entry = draftEntries.getOrNull(draftIndex) ?: return
        DraftPatch.removeEntry(entry.filePath, entry.optionPath)
        refreshDraft()
    }

    // ── Ignore actions ────────────────────────────────────────────────────

    fun showIgnoreDialog() {
        if (ignoreDialogVisible || confirmMessage != null) return
        ignoreDialogSelection = 0
        ignoreDialogVisible = true
    }

    fun showIgnoreDirDialog() {
        val fp = entries.getOrNull(fileIndex)?.key?.toString() ?: return
        ignoreDirDialogPath    = Path.of(fp).parent?.toString() ?: fp
        ignoreDirDialogVisible = true
    }

    fun applyIgnoreDirectory(dir: String) {
        ignoreDirDialogVisible = false
        if (dir.isBlank()) return
        MocSettings.addIgnoredPath(dir.trim())
        McInstanceMocFileSystem.reload()
        McInstanceRefMocFileSystem.reload()
        refreshDiff()
    }

    fun removeIgnoredDirectory(path: Path) {
        MocSettings.removeIgnoredPath(path.toString())
        McInstanceMocFileSystem.reload()
        McInstanceRefMocFileSystem.reload()
        refreshDiff()
    }

    fun applyCurrentIgnore(kind: IgnoreKind) {
        ignoreDialogVisible = false
        val (fp, optionPath, newValue) = when (screen) {
            is Screen.Files -> {
                val fileDiff = entries.getOrNull(fileIndex)?.value ?: return
                val path     = entries.getOrNull(fileIndex)?.key?.toString() ?: return
                val rootPath = if (fileDiff.kind == FileDiffKind.DELETED) "" else "$"
                Triple(path, rootPath, fileDiff.flatContentDiff[rootPath]?.newValue)
            }
            is Screen.Diff -> {
                val path    = entries.getOrNull(fileIndex)?.key?.toString() ?: return
                val visible = visibleDiffItems()
                if (visible.isEmpty()) return
                val sel     = visible[diffIndex]
                val optDiff = entries.getOrNull(fileIndex)?.value?.flatContentDiff?.get(sel) ?: return
                Triple(path, sel, optDiff.newValue)
            }
            is Screen.Value -> {
                val path    = entries.getOrNull(fileIndex)?.key?.toString() ?: return
                val vp      = valuePath ?: return
                val optDiff = entries.getOrNull(fileIndex)?.value?.flatContentDiff?.get(vp) ?: return
                Triple(path, vp, optDiff.newValue)
            }
        }
        val targetValue = if (kind == IgnoreKind.Value) newValue?.toString() else null
        IgnoreStore.add(IgnoreEntry(fp, optionPath, targetValue), kind)
        refreshIgnore()
        refreshDiff()
    }

    fun removeCurrentIgnoreEntry() {
        if (ignoreFilter == IgnoreFilter.Directory) {
            val dir = ignoredDirectories.getOrNull(ignoreIndex) ?: return
            removeIgnoredDirectory(dir)
            return
        }
        val list = currentIgnoreEntriesWithKind()
        val (entry, kind) = list.getOrNull(ignoreIndex) ?: return
        if (kind == IgnoreKind.Directory) {
            removeIgnoredDirectory(Path.of(entry.filePath))
            return
        }
        IgnoreStore.remove(entry.filePath, entry.optionPath, kind)
        refreshIgnore()
        refreshDiff()
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

    private fun loadDiff(): List<Map.Entry<Path, MocFileDiff>> {
        val rawDiff = McInstanceMocFileSystem.diffFrom(McInstanceRefMocFileSystem)

        val stale = IgnoreStore.valueIgnores.filter { ignore ->
            val fileDiff = rawDiff[Path.of(ignore.filePath)] ?: return@filter false
            val optDiff  = fileDiff.flatContentDiff[ignore.optionPath] ?: return@filter false
            optDiff.newValue?.toString() != ignore.targetValue
        }
        stale.forEach { IgnoreStore.remove(it.filePath, it.optionPath, IgnoreKind.Value) }

        return rawDiff.entries
            .sortedBy { it.key.toString() }
            .filter { (path, fileDiff) ->
                val fp = path.toString()
                if (fileDiff.kind == FileDiffKind.DELETED)
                    return@filter !IgnoreStore.isIgnored(fp, "", fileDiff.flatContentDiff[""]?.newValue)
                if (IgnoreStore.isIgnored(fp, "$", fileDiff.flatContentDiff["$"]?.newValue))
                    return@filter false
                val allNonRootPaths = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
                val topLevel = directChildren(allNonRootPaths, "$")
                topLevel.isEmpty() || topLevel.any { !isEffectivelyHidden(fp, it, fileDiff, allNonRootPaths) }
            }
    }
}
