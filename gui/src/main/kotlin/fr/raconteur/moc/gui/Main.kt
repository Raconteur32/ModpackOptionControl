package fr.raconteur.moc.gui

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import fr.raconteur.moc.MocMigration
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.filesystem.McInstanceRefMocFileSystem
import fr.raconteur.moc.platform.PlatformService
import fr.raconteur.moc.versioning.PatchList
import fr.raconteur.moc.versioning.PatchMode

fun main(args: Array<String>) {
    PlatformService.INSTANCE = GuiPlatformService
    if (args.isNotEmpty()) GuiPlatformService.gameDirOverride = java.nio.file.Path.of(args[0])
    MocMigration.migrate()
    GuiPlatformService.getConfigDir().resolve("moc/dev-ref").toFile().let {
        if (it.exists()) it.deleteRecursively()
    }
    PatchList.runStartupCleanup()
    IgnoreStore.pruneRedundant()

    McInstanceMocFileSystem.applyPending()

    McInstanceRefMocFileSystem.regenerateRefFiles()

    application {
        val state = remember { AppState() }

        Window(
            onCloseRequest = ::exitApplication,
            title = "MOC — Modpack Options Control",
            state = rememberWindowState(width = 1200.dp, height = 760.dp),
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else when (state.activeTab) {
                    AppTab.NewPatch -> handleNewPatchKeys(event, state)
                    AppTab.Patches  -> handlePatchesKeys(event, state.patchesState)
                }
            }
        ) {
            App(state)
        }
    }
}

private fun handleNewPatchKeys(event: androidx.compose.ui.input.key.KeyEvent, state: AppState): Boolean {
    val s = state
    return if (s.ignoreDirDialogVisible) {
        if (event.key == Key.Escape) { s.ignoreDirDialogVisible = false; true } else false
    } else if (s.ignoreDialogVisible) {
        val maxSel = if (s.ignoreDialogIsFile) 3 else 2
        when (event.key) {
            Key.DirectionUp   -> { if (s.ignoreDialogSelection > 0) s.ignoreDialogSelection--; true }
            Key.DirectionDown -> { if (s.ignoreDialogSelection < maxSel) s.ignoreDialogSelection++; true }
            Key.Enter         -> {
                if (s.ignoreDialogIsFile && s.ignoreDialogSelection == 3) {
                    s.ignoreDialogVisible = false
                    s.showIgnoreDirDialog()
                } else {
                    s.applyCurrentIgnore(IgnoreKind.entries[s.ignoreDialogSelection])
                }
                true
            }
            Key.Escape -> { s.ignoreDialogVisible = false; true }
            else       -> false
        }
    } else if (s.finalizeDialogVisible) {
        if (event.key == Key.Escape) {
            s.finalizeDialogVisible = false
            s.patchName = ""
            s.patchNameError = null
            true
        } else false
    } else if (s.confirmMessage != null) {
        false
    } else if (event.key == Key.Tab) {
        s.switchFocusNext(); true
    } else if (s.ignoreSearchFocused) {
        if (event.key == Key.Escape) { s.requestClearFocus(); true }
        else false
    } else when (s.focusedPanel) {
        FocusedPanel.Changes -> when (s.screen) {
            is Screen.Files -> when (event.key) {
                Key.DirectionUp               -> { s.moveUp();                            true }
                Key.DirectionDown             -> { s.moveDown();                          true }
                Key.Enter, Key.DirectionRight -> { s.openSelected();                      true }
                Key.Escape, Key.DirectionLeft -> { s.goBack();                            true }
                Key.D                         -> { s.applyCurrentFile(PatchMode.DEFAULT);   true }
                Key.O                         -> { s.applyCurrentFile(PatchMode.OVERRIDE);  true }
                Key.R                         -> { s.removeCurrentFileDraft();             true }
                Key.I                         -> { s.showIgnoreDialog();                   true }
                Key.F -> { if (s.draftEntries.isNotEmpty()) s.finalizeDialogVisible = true; true }
                else  -> false
            }
            is Screen.Diff -> when (event.key) {
                Key.DirectionUp               -> { s.moveUp();                              true }
                Key.DirectionDown             -> { s.moveDown();                            true }
                Key.Enter, Key.DirectionRight -> { s.openSelected();                        true }
                Key.Escape, Key.DirectionLeft -> { s.goBack();                              true }
                Key.D                         -> { s.applyCurrentOption(PatchMode.DEFAULT);   true }
                Key.O                         -> { s.applyCurrentOption(PatchMode.OVERRIDE);  true }
                Key.R                         -> { s.removeCurrentOptionDraft();              true }
                Key.I                         -> { s.showIgnoreDialog();                     true }
                Key.F -> { if (s.draftEntries.isNotEmpty()) s.finalizeDialogVisible = true; true }
                else  -> false
            }
            is Screen.Value -> when (event.key) {
                Key.Escape, Key.DirectionLeft -> { s.goBack();                             true }
                Key.T                         -> { s.valueRawMode = !s.valueRawMode;   true }
                Key.D                         -> { s.applyCurrentValue(PatchMode.DEFAULT);   true }
                Key.O                         -> { s.applyCurrentValue(PatchMode.OVERRIDE);  true }
                Key.R                         -> { s.removeCurrentValueDraft();              true }
                Key.I                         -> { s.showIgnoreDialog();                    true }
                Key.F -> { if (s.draftEntries.isNotEmpty()) s.finalizeDialogVisible = true; true }
                else  -> false
            }
        }
        FocusedPanel.Draft -> when (event.key) {
            Key.DirectionUp   -> { s.moveUp();                   true }
            Key.DirectionDown -> { s.moveDown();                 true }
            Key.R             -> { s.removeCurrentDraftEntry();  true }
            Key.F             -> { if (s.draftEntries.isNotEmpty()) s.finalizeDialogVisible = true; true }
            Key.Escape        -> { s.focusedPanel = FocusedPanel.Changes; true }
            else              -> false
        }
        FocusedPanel.Ignores -> when (event.key) {
            Key.DirectionUp   -> { s.moveUp();                      true }
            Key.DirectionDown -> { s.moveDown();                    true }
            Key.R             -> { s.removeCurrentIgnoreEntry();    true }
            Key.Escape        -> { s.focusedPanel = FocusedPanel.Changes; true }
            else              -> false
        }
    }
}

private fun handlePatchesKeys(event: androidx.compose.ui.input.key.KeyEvent, ps: PatchesState): Boolean {
    return when (ps.view) {
        PatchesView.List -> handlePatchListKeys(event, ps)
        PatchesView.Content -> when (event.key) {
            Key.Escape, Key.DirectionLeft -> { ps.view = PatchesView.List; true }
            else -> false
        }
        PatchesView.Recomposition -> handleRecompKeys(event, ps)
    }
}

private fun handlePatchListKeys(event: androidx.compose.ui.input.key.KeyEvent, ps: PatchesState): Boolean {
    if (ps.deleteConfirmVisible || ps.recompConflictVisible) return false
    return when (event.key) {
        Key.DirectionUp   -> { if (ps.patchIndex > 0) { ps.patchIndex--; ps.rangeAnchor = ps.patchIndex }; true }
        Key.DirectionDown -> { if (ps.patchIndex < ps.patches.size - 1) { ps.patchIndex++; ps.rangeAnchor = ps.patchIndex }; true }
        Key.Enter, Key.DirectionRight -> { if (ps.patches.isNotEmpty()) ps.view = PatchesView.Content; true }
        Key.Delete, Key.Backspace     -> { if (ps.patches.isNotEmpty()) ps.deleteConfirmVisible = true; true }
        Key.R -> {
            val range = ps.selectedRange
            if (range != null) ps.tryLaunchRecomposition(range.first, range.last)
            else if (ps.patches.isNotEmpty()) ps.tryLaunchRecomposition(ps.patchIndex, ps.patchIndex)
            true
        }
        else -> false
    }
}

private fun handleRecompKeys(event: androidx.compose.ui.input.key.KeyEvent, ps: PatchesState): Boolean {
    if (ps.recompFinalizeDialogVisible) {
        return if (event.key == Key.Escape) {
            ps.recompFinalizeDialogVisible = false
            ps.recompPatchName      = ""
            ps.recompPatchNameError = null
            true
        } else false
    }
    if (ps.recompConfirmMessage != null) return false

    return if (event.key == Key.Tab) {
        ps.recompSwitchFocusNext(); true
    } else when (ps.recompFocusedPanel) {
        RecompFocusedPanel.Changes -> when (ps.recompScreen) {
            is Screen.Files -> when (event.key) {
                Key.DirectionUp               -> { ps.recompMoveUp();                                true }
                Key.DirectionDown             -> { ps.recompMoveDown();                              true }
                Key.Enter, Key.DirectionRight -> { ps.recompOpenSelected();                          true }
                Key.Escape, Key.DirectionLeft -> { ps.view = PatchesView.List;                       true }
                Key.D                         -> { ps.recompApplyCurrentFile(PatchMode.DEFAULT);     true }
                Key.O                         -> { ps.recompApplyCurrentFile(PatchMode.OVERRIDE);    true }
                Key.R                         -> { ps.recompRemoveCurrentFileDraft();                true }
                Key.I -> {
                    val fp = ps.recompEntries.getOrNull(ps.recompFileIndex)?.key?.toString()
                    if (fp != null) ps.recompAddIgnore(fp, "$")
                    true
                }
                Key.F -> { if (ps.recompDraftEntries.isNotEmpty()) ps.recompFinalizeDialogVisible = true; true }
                else  -> false
            }
            is Screen.Diff -> when (event.key) {
                Key.DirectionUp               -> { ps.recompMoveUp();                                true }
                Key.DirectionDown             -> { ps.recompMoveDown();                              true }
                Key.Enter, Key.DirectionRight -> { ps.recompOpenSelected();                          true }
                Key.Escape, Key.DirectionLeft -> { ps.recompGoBack();                                true }
                Key.D                         -> { ps.recompApplyCurrentOption(PatchMode.DEFAULT);   true }
                Key.O                         -> { ps.recompApplyCurrentOption(PatchMode.OVERRIDE);  true }
                Key.R                         -> { ps.recompRemoveCurrentOptionDraft();              true }
                Key.I -> {
                    val fp      = ps.recompEntries.getOrNull(ps.recompFileIndex)?.key?.toString()
                    val visible = ps.recompVisibleDiffItems()
                    if (fp != null && ps.recompDiffIndex < visible.size)
                        ps.recompAddIgnore(fp, visible[ps.recompDiffIndex])
                    true
                }
                Key.F -> { if (ps.recompDraftEntries.isNotEmpty()) ps.recompFinalizeDialogVisible = true; true }
                else  -> false
            }
            is Screen.Value -> when (event.key) {
                Key.Escape, Key.DirectionLeft -> { ps.recompGoBack();                                true }
                Key.D                         -> { ps.recompApplyCurrentValue(PatchMode.DEFAULT);    true }
                Key.O                         -> { ps.recompApplyCurrentValue(PatchMode.OVERRIDE);   true }
                Key.R                         -> { ps.recompRemoveCurrentValueDraft();               true }
                Key.I -> {
                    val fp = ps.recompEntries.getOrNull(ps.recompFileIndex)?.key?.toString()
                    val vp = ps.recompValuePath
                    if (fp != null && vp != null) ps.recompAddIgnore(fp, vp)
                    true
                }
                Key.F -> { if (ps.recompDraftEntries.isNotEmpty()) ps.recompFinalizeDialogVisible = true; true }
                else  -> false
            }
        }
        RecompFocusedPanel.Draft -> when (event.key) {
            Key.DirectionUp   -> { ps.recompMoveUp();                        true }
            Key.DirectionDown -> { ps.recompMoveDown();                      true }
            Key.R             -> { ps.recompRemoveCurrentDraftEntry();       true }
            Key.F             -> { if (ps.recompDraftEntries.isNotEmpty()) ps.recompFinalizeDialogVisible = true; true }
            Key.Escape        -> { ps.recompFocusedPanel = RecompFocusedPanel.Changes; true }
            else              -> false
        }
        RecompFocusedPanel.Ignores -> when (event.key) {
            Key.DirectionUp   -> { ps.recompMoveUp();                        true }
            Key.DirectionDown -> { ps.recompMoveDown();                      true }
            Key.R             -> { ps.recompRemoveCurrentIgnore();           true }
            Key.Escape        -> { ps.recompFocusedPanel = RecompFocusedPanel.Changes; true }
            else              -> false
        }
    }
}
