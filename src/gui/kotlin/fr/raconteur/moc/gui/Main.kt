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
import fr.raconteur.moc.filesystem.McInstanceRefMocFileSystem
import fr.raconteur.moc.platform.PlatformService
import fr.raconteur.moc.versioning.PatchMode

fun main() {
    PlatformService.INSTANCE = GuiPlatformService
    McInstanceRefMocFileSystem.regenerateRefFiles()

    application {
        val state = remember { AppState() }

        Window(
            onCloseRequest = ::exitApplication,
            title = "MOC — Modpack Options Control",
            state = rememberWindowState(width = 1100.dp, height = 720.dp),
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else if (state.ignoreDialogVisible) {
                    when (event.key) {
                        Key.One    -> { state.applyCurrentIgnore(IgnoreKind.Session);   true }
                        Key.Two    -> { state.applyCurrentIgnore(IgnoreKind.Value);     true }
                        Key.Three  -> { state.applyCurrentIgnore(IgnoreKind.Permanent); true }
                        Key.Escape -> { state.ignoreDialogVisible = false;              true }
                        else -> false
                    }
                } else if (state.confirmMessage != null) {
                    false
                } else if (event.key == Key.Tab) {
                    state.switchToNextTab(); true
                } else when (state.screen) {
                    is Screen.Files -> when (event.key) {
                        Key.DirectionUp                       -> { state.moveUp();                           true }
                        Key.DirectionDown                     -> { state.moveDown();                         true }
                        Key.Enter, Key.DirectionRight         -> { state.openSelected();                     true }
                        Key.Escape, Key.DirectionLeft         -> { state.goBack();                           true }
                        Key.D                                 -> { state.applyCurrentFile(PatchMode.DEFAULT);  true }
                        Key.O                                 -> { state.applyCurrentFile(PatchMode.OVERRIDE); true }
                        Key.R                                 -> { state.removeCurrentFileDraft();            true }
                        Key.I                                 -> { state.showIgnoreDialog();                  true }
                        Key.E -> {
                            if (state.draftEntries.isNotEmpty()) { state.draftIndex = 0; state.screen = Screen.Draft }
                            true
                        }
                        Key.F -> {
                            if (state.draftEntries.isNotEmpty()) state.screen = Screen.Finalize
                            true
                        }
                        else -> false
                    }
                    is Screen.Diff -> when (event.key) {
                        Key.DirectionUp                       -> { state.moveUp();                             true }
                        Key.DirectionDown                     -> { state.moveDown();                           true }
                        Key.Enter, Key.DirectionRight         -> { state.openSelected();                       true }
                        Key.Escape, Key.DirectionLeft         -> { state.goBack();                             true }
                        Key.D                                 -> { state.applyCurrentOption(PatchMode.DEFAULT);  true }
                        Key.O                                 -> { state.applyCurrentOption(PatchMode.OVERRIDE); true }
                        Key.R                                 -> { state.removeCurrentOptionDraft();             true }
                        Key.I                                 -> { state.showIgnoreDialog();                    true }
                        else -> false
                    }
                    is Screen.Draft -> when (event.key) {
                        Key.DirectionUp               -> { state.moveUp();                 true }
                        Key.DirectionDown             -> { state.moveDown();               true }
                        Key.Escape, Key.DirectionLeft -> { state.goBack();                 true }
                        Key.R                         -> { state.removeCurrentDraftEntry(); true }
                        Key.F -> {
                            if (state.draftEntries.isNotEmpty()) state.screen = Screen.Finalize
                            true
                        }
                        else -> false
                    }
                    is Screen.Value -> when (event.key) {
                        Key.Escape, Key.DirectionLeft -> { state.goBack();                           true }
                        Key.T                         -> { state.valueRawMode = !state.valueRawMode;  true }
                        Key.D                         -> { state.applyCurrentValue(PatchMode.DEFAULT);  true }
                        Key.O                         -> { state.applyCurrentValue(PatchMode.OVERRIDE); true }
                        Key.R                         -> { state.removeCurrentValueDraft();             true }
                        Key.I                         -> { state.showIgnoreDialog();                    true }
                        else -> false
                    }
                    is Screen.IgnoreSession, is Screen.IgnoreValue, is Screen.IgnorePermanent -> when (event.key) {
                        Key.DirectionUp               -> { state.moveUp();                    true }
                        Key.DirectionDown             -> { state.moveDown();                  true }
                        Key.Escape                    -> { state.goBack();                    true }
                        Key.R                         -> { state.removeCurrentIgnoreEntry();  true }
                        else -> false
                    }
                    else -> false
                }
            }
        ) {
            App(state)
        }
    }
}
