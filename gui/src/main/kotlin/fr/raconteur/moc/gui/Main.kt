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
import fr.raconteur.moc.versioning.PatchMode

fun main(args: Array<String>) {
    PlatformService.INSTANCE = GuiPlatformService
    if (args.isNotEmpty()) GuiPlatformService.gameDirOverride = java.nio.file.Path.of(args[0])
    MocMigration.migrate()
    GuiPlatformService.getConfigDir().resolve("moc/dev-ref").toFile().let {
        if (it.exists()) it.deleteRecursively()
    }
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
                } else if (state.ignoreDirDialogVisible) {
                    if (event.key == Key.Escape) { state.ignoreDirDialogVisible = false; true } else false
                } else if (state.ignoreDialogVisible) {
                    val maxSel = if (state.ignoreDialogIsFile) 3 else 2
                    when (event.key) {
                        Key.DirectionUp   -> { if (state.ignoreDialogSelection > 0) state.ignoreDialogSelection--; true }
                        Key.DirectionDown -> { if (state.ignoreDialogSelection < maxSel) state.ignoreDialogSelection++; true }
                        Key.Enter         -> {
                            if (state.ignoreDialogIsFile && state.ignoreDialogSelection == 3) {
                                state.ignoreDialogVisible = false
                                state.showIgnoreDirDialog()
                            } else {
                                state.applyCurrentIgnore(IgnoreKind.entries[state.ignoreDialogSelection])
                            }
                            true
                        }
                        Key.Escape -> { state.ignoreDialogVisible = false; true }
                        else       -> false
                    }
                } else if (state.finalizeDialogVisible) {
                    if (event.key == Key.Escape) {
                        state.finalizeDialogVisible = false
                        state.patchName = ""
                        state.patchNameError = null
                        true
                    } else false
                } else if (state.confirmMessage != null) {
                    false
                } else if (event.key == Key.Tab) {
                    state.switchFocusNext(); true
                } else if (state.ignoreSearchFocused) {
                    if (event.key == Key.Escape) { state.requestClearFocus(); true }
                    else false
                } else when (state.focusedPanel) {
                    FocusedPanel.Changes -> when (state.screen) {
                        is Screen.Files -> when (event.key) {
                            Key.DirectionUp               -> { state.moveUp();                            true }
                            Key.DirectionDown             -> { state.moveDown();                          true }
                            Key.Enter, Key.DirectionRight -> { state.openSelected();                      true }
                            Key.Escape, Key.DirectionLeft -> { state.goBack();                            true }
                            Key.D                         -> { state.applyCurrentFile(PatchMode.DEFAULT);   true }
                            Key.O                         -> { state.applyCurrentFile(PatchMode.OVERRIDE);  true }
                            Key.R                         -> { state.removeCurrentFileDraft();             true }
                            Key.I                         -> { state.showIgnoreDialog();                   true }
                            Key.F -> { if (state.draftEntries.isNotEmpty()) state.finalizeDialogVisible = true; true }
                            else  -> false
                        }
                        is Screen.Diff -> when (event.key) {
                            Key.DirectionUp               -> { state.moveUp();                              true }
                            Key.DirectionDown             -> { state.moveDown();                            true }
                            Key.Enter, Key.DirectionRight -> { state.openSelected();                        true }
                            Key.Escape, Key.DirectionLeft -> { state.goBack();                              true }
                            Key.D                         -> { state.applyCurrentOption(PatchMode.DEFAULT);   true }
                            Key.O                         -> { state.applyCurrentOption(PatchMode.OVERRIDE);  true }
                            Key.R                         -> { state.removeCurrentOptionDraft();              true }
                            Key.I                         -> { state.showIgnoreDialog();                     true }
                            Key.F -> { if (state.draftEntries.isNotEmpty()) state.finalizeDialogVisible = true; true }
                            else  -> false
                        }
                        is Screen.Value -> when (event.key) {
                            Key.Escape, Key.DirectionLeft -> { state.goBack();                             true }
                            Key.T                         -> { state.valueRawMode = !state.valueRawMode;   true }
                            Key.D                         -> { state.applyCurrentValue(PatchMode.DEFAULT);   true }
                            Key.O                         -> { state.applyCurrentValue(PatchMode.OVERRIDE);  true }
                            Key.R                         -> { state.removeCurrentValueDraft();              true }
                            Key.I                         -> { state.showIgnoreDialog();                    true }
                            Key.F -> { if (state.draftEntries.isNotEmpty()) state.finalizeDialogVisible = true; true }
                            else  -> false
                        }
                    }
                    FocusedPanel.Draft -> when (event.key) {
                        Key.DirectionUp   -> { state.moveUp();                   true }
                        Key.DirectionDown -> { state.moveDown();                 true }
                        Key.R             -> { state.removeCurrentDraftEntry();  true }
                        Key.F             -> { if (state.draftEntries.isNotEmpty()) state.finalizeDialogVisible = true; true }
                        Key.Escape        -> { state.focusedPanel = FocusedPanel.Changes; true }
                        else              -> false
                    }
                    FocusedPanel.Ignores -> when (event.key) {
                        Key.DirectionUp   -> { state.moveUp();                      true }
                        Key.DirectionDown -> { state.moveDown();                    true }
                        Key.R             -> { state.removeCurrentIgnoreEntry();    true }
                        Key.Escape        -> { state.focusedPanel = FocusedPanel.Changes; true }
                        else              -> false
                    }
                }
            }
        ) {
            App(state)
        }
    }
}
