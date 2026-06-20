package fr.raconteur.moc.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import fr.raconteur.moc.gui.components.*
import fr.raconteur.moc.gui.screens.*

@Composable
fun App(state: AppState) {
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.focusedPanel) {
        if (state.focusedPanel != FocusedPanel.Ignores) focusManager.clearFocus()
    }
    LaunchedEffect(state.clearFocusGeneration) {
        if (state.clearFocusGeneration > 0) focusManager.clearFocus()
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tab bar
                TabBar(activeTab = state.activeTab) { state.activeTab = it }

                // Tab content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state.activeTab) {
                        AppTab.NewPatch -> NewPatchTab(state)
                        AppTab.Patches  -> PatchesTab(state.patchesState)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewPatchTab(state: AppState) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalW = constraints.maxWidth.toFloat()
        val totalH = constraints.maxHeight.toFloat()

        var leftFraction by remember { mutableStateOf(0.40f) }
        var topFraction  by remember { mutableStateOf(0.50f) }

        Row(modifier = Modifier.fillMaxSize()) {
            // Left panel: changes navigation
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(leftFraction)
                    .panelFocusInput { state.focusedPanel = FocusedPanel.Changes }
            ) {
                FocusLine(state.focusedPanel == FocusedPanel.Changes)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (val screen = state.screen) {
                        is Screen.Files -> FilesScreen(state)
                        is Screen.Diff  -> DiffScreen(state)
                        is Screen.Value -> ValueScreen(state, screen.returnTo)
                    }
                }
            }

            // Vertical divider
            PanelDivider(horizontal = false) { delta ->
                if (totalW > 0f)
                    leftFraction = (leftFraction + delta / totalW).coerceIn(0.15f, 0.80f)
            }

            // Right column
            Column(modifier = Modifier.fillMaxHeight().weight(1f - leftFraction)) {
                // Draft panel (top-right)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(topFraction)
                        .panelFocusInput { state.focusedPanel = FocusedPanel.Draft }
                ) {
                    FocusLine(state.focusedPanel == FocusedPanel.Draft)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        DraftPanel(state)
                    }
                }

                // Horizontal divider
                PanelDivider(horizontal = true) { delta ->
                    if (totalH > 0f)
                        topFraction = (topFraction + delta / totalH).coerceIn(0.10f, 0.90f)
                }

                // Ignore panel (bottom-right)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f - topFraction)
                        .panelFocusInput { state.focusedPanel = FocusedPanel.Ignores }
                ) {
                    FocusLine(state.focusedPanel == FocusedPanel.Ignores)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        IgnorePanel(state)
                    }
                }
            }
        }

        // Dialogs — overlays on top of the panels
        state.confirmMessage?.let { msg ->
            ConfirmDialog(
                message   = msg,
                onConfirm = {
                    state.confirmAction?.invoke()
                    state.confirmMessage = null
                    state.confirmAction  = null
                },
                onDismiss = {
                    state.confirmMessage = null
                    state.confirmAction  = null
                }
            )
        }

        if (state.ignoreDialogVisible) {
            IgnoreDialog(
                selection   = state.ignoreDialogSelection,
                isFile      = state.ignoreDialogIsFile,
                onIgnore    = { kind -> state.applyCurrentIgnore(kind) },
                onIgnoreDir = {
                    state.ignoreDialogVisible = false
                    state.showIgnoreDirDialog()
                },
                onDismiss   = { state.ignoreDialogVisible = false }
            )
        }

        if (state.ignoreDirDialogVisible) {
            IgnoreDirDialog(
                path         = state.ignoreDirDialogPath,
                onPathChange = { state.ignoreDirDialogPath = it },
                onConfirm    = { state.applyIgnoreDirectory(state.ignoreDirDialogPath) },
                onDismiss    = { state.ignoreDirDialogVisible = false }
            )
        }

        if (state.finalizeDialogVisible) {
            FinalizeDialog(state)
        }
    }
}

// Detects pointer presses without consuming events so children still receive them.
private fun Modifier.panelFocusInput(onPress: () -> Unit): Modifier =
    pointerInput(onPress) {
        awaitEachGesture {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.pressed && !it.previousPressed }) onPress()
        }
    }

@Composable
private fun FocusLine(focused: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(
                if (focused) MaterialTheme.colors.primary
                else Color.Transparent
            )
    )
}
