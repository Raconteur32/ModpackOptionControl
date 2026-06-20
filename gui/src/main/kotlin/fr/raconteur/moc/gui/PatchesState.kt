package fr.raconteur.moc.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.raconteur.moc.filesystem.FileDiffKind
import fr.raconteur.moc.filesystem.MocFileDiff
import fr.raconteur.moc.filesystem.directChildren
import fr.raconteur.moc.filesystem.isDescendant
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchList
import fr.raconteur.moc.versioning.PatchMode
import fr.raconteur.moc.versioning.RecompositionDraft
import java.nio.file.Path

enum class PatchesView { List, Content, Recomposition }

enum class RecompFocusedPanel { Changes, Draft, Ignores }

class PatchesState {

    // ── Patch list ────────────────────────────────────────────────────────────

    var patches     by mutableStateOf(PatchList.getAll())
    var patchIndex  by mutableStateOf(0)
    var rangeAnchor by mutableStateOf<Int?>(null)

    var view by mutableStateOf(
        if (RecompositionDraft.hasActiveDraft()) PatchesView.Recomposition else PatchesView.List
    )

    var deleteConfirmVisible  by mutableStateOf(false)
    var recompConflictVisible by mutableStateOf(false)
    var pendingRecompStart    by mutableStateOf<Int?>(null)
    var pendingRecompEnd      by mutableStateOf<Int?>(null)

    val selectedRange: IntRange?
        get() {
            val anchor = rangeAnchor ?: return null
            val end    = patchIndex
            return if (anchor <= end) anchor..end else end..anchor
        }

    fun refreshPatches() {
        patches    = PatchList.getAll()
        patchIndex = patchIndex.coerceIn(0, (patches.size - 1).coerceAtLeast(0))
    }

    fun tryLaunchRecomposition(startIdx: Int, endIdx: Int) {
        if (RecompositionDraft.hasActiveDraft()) {
            pendingRecompStart    = startIdx
            pendingRecompEnd      = endIdx
            recompConflictVisible = true
        } else {
            launchRecomposition(startIdx, endIdx)
        }
    }

    fun launchRecomposition(startIdx: Int, endIdx: Int) {
        RecompositionDraft.build(startIdx, endIdx)
        recompEntries      = RecompositionDraft.cachedDiff
        recompDraftEntries = emptyList()
        recompIgnores      = emptyList()
        recompScreen       = Screen.Files
        recompFileIndex    = 0
        recompDiffIndex    = 0
        recompDraftIndex   = 0
        recompIgnoreIndex  = 0
        recompPathStack    = listOf("$")
        recompFocusedPanel = RecompFocusedPanel.Changes
        view               = PatchesView.Recomposition
    }

    fun refreshRecompEntries() {
        recompEntries   = RecompositionDraft.cachedDiff
        recompFileIndex = recompFileIndex.coerceIn(0, (recompEntries.size - 1).coerceAtLeast(0))
        if (recompEntries.isEmpty()) recompScreen = Screen.Files
        recompDiffIndex = recompDiffIndex.coerceIn(0, (recompVisibleDiffItems().size - 1).coerceAtLeast(0))
    }

    fun resumeRecomposition() {
        recompEntries      = RecompositionDraft.cachedDiff
        recompDraftEntries = RecompositionDraft.entries.toList()
        recompIgnores      = IgnoreStore.recompositionIgnores
        recompScreen       = Screen.Files
        recompFileIndex    = 0
        recompDiffIndex    = 0
        recompDraftIndex   = 0
        recompIgnoreIndex  = 0
        recompPathStack    = listOf("$")
        recompFocusedPanel = RecompFocusedPanel.Changes
        view               = PatchesView.Recomposition
    }

    // ── Recomposition editor state ────────────────────────────────────────────

    var recompEntries      by mutableStateOf(RecompositionDraft.cachedDiff)
    var recompDraftEntries by mutableStateOf<List<PatchEntry>>(RecompositionDraft.entries.toList())
    var recompIgnores      by mutableStateOf(IgnoreStore.recompositionIgnores)

    var recompScreen        by mutableStateOf<Screen>(Screen.Files)
    var recompFileIndex     by mutableStateOf(0)
    var recompDiffIndex     by mutableStateOf(0)
    var recompDraftIndex    by mutableStateOf(0)
    var recompIgnoreIndex   by mutableStateOf(0)
    var recompPathStack     by mutableStateOf(listOf("$"))
    var recompFocusedPanel  by mutableStateOf(RecompFocusedPanel.Changes)
    var recompValuePath     by mutableStateOf<String?>(null)

    var recompFinalizeDialogVisible by mutableStateOf(false)
    var recompPatchName             by mutableStateOf("")
    var recompPatchNameError        by mutableStateOf<String?>(null)

    var recompConfirmMessage by mutableStateOf<String?>(null)
    var recompConfirmAction  by mutableStateOf<(() -> Unit)?>(null)

    fun refreshRecompDraft() {
        recompDraftEntries = RecompositionDraft.entries.toList()
        recompDraftIndex   = recompDraftIndex.coerceIn(0, (recompDraftEntries.size - 1).coerceAtLeast(0))
    }

    fun refreshRecompIgnores() {
        recompIgnores     = IgnoreStore.recompositionIgnores
        recompIgnoreIndex = recompIgnoreIndex.coerceIn(0, (recompIgnores.size - 1).coerceAtLeast(0))
    }

    fun recompCurrentFilePath(): Path? = recompEntries.getOrNull(recompFileIndex)?.key

    // ── Recomp navigation ─────────────────────────────────────────────────────

    fun recompSwitchFocusNext() {
        recompFocusedPanel = when (recompFocusedPanel) {
            RecompFocusedPanel.Changes -> RecompFocusedPanel.Draft
            RecompFocusedPanel.Draft   -> RecompFocusedPanel.Ignores
            RecompFocusedPanel.Ignores -> RecompFocusedPanel.Changes
        }
    }

    fun recompMoveUp() {
        if (recompConfirmMessage != null) return
        when (recompFocusedPanel) {
            RecompFocusedPanel.Changes -> when (recompScreen) {
                is Screen.Files -> if (recompFileIndex > 0) recompFileIndex--
                is Screen.Diff  -> if (recompDiffIndex > 0) recompDiffIndex--
                else -> {}
            }
            RecompFocusedPanel.Draft   -> if (recompDraftIndex > 0) recompDraftIndex--
            RecompFocusedPanel.Ignores -> if (recompIgnoreIndex > 0) recompIgnoreIndex--
        }
    }

    fun recompMoveDown() {
        if (recompConfirmMessage != null) return
        when (recompFocusedPanel) {
            RecompFocusedPanel.Changes -> when (recompScreen) {
                is Screen.Files -> if (recompFileIndex < recompEntries.size - 1) recompFileIndex++
                is Screen.Diff  -> { val n = recompVisibleDiffItems().size; if (recompDiffIndex < n - 1) recompDiffIndex++ }
                else -> {}
            }
            RecompFocusedPanel.Draft   -> if (recompDraftIndex < recompDraftEntries.size - 1) recompDraftIndex++
            RecompFocusedPanel.Ignores -> if (recompIgnoreIndex < recompIgnores.size - 1) recompIgnoreIndex++
        }
    }

    fun recompOpenSelected() {
        if (recompConfirmMessage != null) return
        when (recompScreen) {
            is Screen.Files -> recompNavigateIntoFile()
            is Screen.Diff  -> recompNavigateIntoDiff()
            else -> {}
        }
    }

    fun recompGoBack() {
        if (recompConfirmMessage != null) return
        when (val s = recompScreen) {
            is Screen.Diff  -> if (recompPathStack.size > 1) { recompPathStack = recompPathStack.dropLast(1); recompDiffIndex = 0 }
                               else recompScreen = Screen.Files
            is Screen.Value -> recompScreen = s.returnTo
            else -> {}
        }
    }

    // ── Recomp diff items ─────────────────────────────────────────────────────

    fun recompVisibleDiffItems(): List<String> {
        val fileDiff = recompEntries.getOrNull(recompFileIndex)?.value ?: return emptyList()
        val allNonRoot = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
        return directChildren(allNonRoot, recompPathStack.last()).filter { path ->
            !isRecompEffectivelyHidden(recompEntries.getOrNull(recompFileIndex)?.key?.toString() ?: "", path, fileDiff, allNonRoot)
        }
    }

    private fun isRecompEffectivelyHidden(fp: String, path: String, fileDiff: MocFileDiff, allNonRoot: List<String>): Boolean {
        if (IgnoreStore.isIgnoredForRecomp(fp, path)) return true
        val children = directChildren(allNonRoot, path)
        if (children.isEmpty()) return false
        return children.all { isRecompEffectivelyHidden(fp, it, fileDiff, allNonRoot) }
    }

    // ── Recomp file-level actions ─────────────────────────────────────────────

    fun recompCurrentFileDraftEntry(): PatchEntry? {
        val fp = recompEntries.getOrNull(recompFileIndex)?.key?.toString() ?: return null
        return recompDraftEntries.firstOrNull { it.filePath == fp && (it.optionPath == "$" || it.optionPath == "") }
    }

    fun recompApplyCurrentFile(mode: PatchMode) {
        if (recompConfirmMessage != null) return
        val filePath = recompEntries.getOrNull(recompFileIndex)?.key ?: return
        val fileDiff = recompEntries.getOrNull(recompFileIndex)?.value ?: return
        val optDiff  = if (fileDiff.kind == FileDiffKind.DELETED)
            fileDiff.flatContentDiff[""] else fileDiff.flatContentDiff["$"]
        optDiff ?: return
        val fpStr       = filePath.toString()
        val parentEntry = recompDraftEntries.firstOrNull { it.filePath == fpStr && isDescendant(optDiff.path, it.optionPath) }
        val children    = recompDraftEntries.filter    { it.filePath == fpStr && isDescendant(it.optionPath, optDiff.path) }
        when {
            parentEntry != null -> {
                recompConfirmMessage = "Parent entry « ${parentEntry.optionPath} » [${parentEntry.mode}] will be removed."
                recompConfirmAction  = { RecompositionDraft.removeEntry(parentEntry.filePath, parentEntry.optionPath); RecompositionDraft.applyDiff(optDiff, mode); refreshRecompDraft() }
            }
            children.isNotEmpty() -> {
                recompConfirmMessage = "${children.size} sub-entr${if (children.size > 1) "ies" else "y"} will be removed."
                recompConfirmAction  = { children.forEach { RecompositionDraft.removeEntry(it.filePath, it.optionPath) }; RecompositionDraft.applyDiff(optDiff, mode); refreshRecompDraft() }
            }
            else -> { RecompositionDraft.applyDiff(optDiff, mode); refreshRecompDraft() }
        }
    }

    fun recompRemoveCurrentFileDraft() {
        if (recompConfirmMessage != null) return
        val entry = recompCurrentFileDraftEntry() ?: return
        RecompositionDraft.removeEntry(entry.filePath, entry.optionPath)
        refreshRecompDraft()
    }

    // ── Recomp option-level actions ───────────────────────────────────────────

    fun recompCurrentOptionDraftEntry(): PatchEntry? {
        val fp      = recompEntries.getOrNull(recompFileIndex)?.key?.toString() ?: return null
        val visible = recompVisibleDiffItems()
        if (recompDiffIndex >= visible.size) return null
        return recompDraftEntries.find { it.filePath == fp && it.optionPath == visible[recompDiffIndex] }
    }

    fun recompApplyCurrentOption(mode: PatchMode) {
        if (recompConfirmMessage != null) return
        val filePath = recompEntries.getOrNull(recompFileIndex)?.key?.toString() ?: return
        val visible  = recompVisibleDiffItems()
        if (recompDiffIndex >= visible.size) return
        val selected = visible[recompDiffIndex]
        val fileDiff = recompEntries.getOrNull(recompFileIndex)?.value ?: return
        val optDiff  = fileDiff.flatContentDiff[selected] ?: return
        val parentEntry = recompDraftEntries.firstOrNull { it.filePath == filePath && isDescendant(selected, it.optionPath) }
        val children    = recompDraftEntries.filter    { it.filePath == filePath && isDescendant(it.optionPath, selected) }
        when {
            parentEntry != null -> {
                recompConfirmMessage = "Parent entry « ${parentEntry.optionPath} » [${parentEntry.mode}] will be removed."
                recompConfirmAction  = { RecompositionDraft.removeEntry(parentEntry.filePath, parentEntry.optionPath); RecompositionDraft.applyDiff(optDiff, mode); refreshRecompDraft() }
            }
            children.isNotEmpty() -> {
                recompConfirmMessage = "${children.size} sub-entr${if (children.size > 1) "ies" else "y"} will be removed."
                recompConfirmAction  = { children.forEach { RecompositionDraft.removeEntry(it.filePath, it.optionPath) }; RecompositionDraft.applyDiff(optDiff, mode); refreshRecompDraft() }
            }
            else -> { RecompositionDraft.applyDiff(optDiff, mode); refreshRecompDraft() }
        }
    }

    fun recompRemoveCurrentOptionDraft() {
        if (recompConfirmMessage != null) return
        val filePath = recompEntries.getOrNull(recompFileIndex)?.key?.toString() ?: return
        val visible  = recompVisibleDiffItems()
        if (recompDiffIndex >= visible.size) return
        RecompositionDraft.removeEntry(filePath, visible[recompDiffIndex])
        refreshRecompDraft()
    }

    // ── Recomp value-level actions ────────────────────────────────────────────

    fun recompCurrentValueDraftEntry(): PatchEntry? {
        val fp = recompEntries.getOrNull(recompFileIndex)?.key?.toString() ?: return null
        val vp = recompValuePath ?: return null
        return recompDraftEntries.find { it.filePath == fp && it.optionPath == vp }
    }

    fun recompApplyCurrentValue(mode: PatchMode) {
        if (recompConfirmMessage != null) return
        val filePath = recompEntries.getOrNull(recompFileIndex)?.key?.toString() ?: return
        val vp       = recompValuePath ?: return
        val fileDiff = recompEntries.getOrNull(recompFileIndex)?.value ?: return
        val optDiff  = fileDiff.flatContentDiff[vp] ?: return
        val parentEntry = recompDraftEntries.firstOrNull { it.filePath == filePath && isDescendant(vp, it.optionPath) }
        val children    = recompDraftEntries.filter    { it.filePath == filePath && isDescendant(it.optionPath, vp) }
        when {
            parentEntry != null -> {
                recompConfirmMessage = "Parent entry « ${parentEntry.optionPath} » [${parentEntry.mode}] will be removed."
                recompConfirmAction  = { RecompositionDraft.removeEntry(parentEntry.filePath, parentEntry.optionPath); RecompositionDraft.applyDiff(optDiff, mode); refreshRecompDraft() }
            }
            children.isNotEmpty() -> {
                recompConfirmMessage = "${children.size} sub-entr${if (children.size > 1) "ies" else "y"} will be removed."
                recompConfirmAction  = { children.forEach { RecompositionDraft.removeEntry(it.filePath, it.optionPath) }; RecompositionDraft.applyDiff(optDiff, mode); refreshRecompDraft() }
            }
            else -> { RecompositionDraft.applyDiff(optDiff, mode); refreshRecompDraft() }
        }
    }

    fun recompRemoveCurrentValueDraft() {
        if (recompConfirmMessage != null) return
        val fp = recompEntries.getOrNull(recompFileIndex)?.key?.toString() ?: return
        val vp = recompValuePath ?: return
        RecompositionDraft.removeEntry(fp, vp)
        refreshRecompDraft()
    }

    // ── Recomp draft panel ────────────────────────────────────────────────────

    fun recompRemoveCurrentDraftEntry() {
        if (recompConfirmMessage != null) return
        val entry = recompDraftEntries.getOrNull(recompDraftIndex) ?: return
        RecompositionDraft.removeEntry(entry.filePath, entry.optionPath)
        refreshRecompDraft()
    }

    // ── Recomp ignore ─────────────────────────────────────────────────────────

    fun recompAddIgnore(filePath: String, optionPath: String) {
        IgnoreStore.addRecomp(IgnoreEntry(filePath, optionPath))
        refreshRecompIgnores()
    }

    fun recompRemoveCurrentIgnore() {
        val entry = recompIgnores.getOrNull(recompIgnoreIndex) ?: return
        IgnoreStore.removeRecomp(entry.filePath, entry.optionPath)
        refreshRecompIgnores()
    }

    // ── Private navigation ────────────────────────────────────────────────────

    private fun recompNavigateIntoFile() {
        val fileDiff = recompEntries.getOrNull(recompFileIndex)?.value ?: return
        val allPaths = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
        if (directChildren(allPaths, "$").isNotEmpty()) {
            recompPathStack = listOf("$"); recompDiffIndex = 0; recompScreen = Screen.Diff
        } else {
            recompValuePath = "$"; recompScreen = Screen.Value(returnTo = Screen.Files)
        }
    }

    private fun recompNavigateIntoDiff() {
        val visible  = recompVisibleDiffItems()
        if (visible.isEmpty()) return
        val sel      = visible[recompDiffIndex]
        val fileDiff = recompEntries.getOrNull(recompFileIndex)?.value ?: return
        val allPaths = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
        if (directChildren(allPaths, sel).isNotEmpty()) {
            recompPathStack = recompPathStack + sel; recompDiffIndex = 0
        } else {
            recompValuePath = sel; recompScreen = Screen.Value(returnTo = Screen.Diff)
        }
    }
}
