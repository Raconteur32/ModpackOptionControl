package fr.raconteur.moc.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import fr.raconteur.moc.gui.PatchesState
import fr.raconteur.moc.gui.PatchesView
import fr.raconteur.moc.gui.RecompFocusedPanel
import fr.raconteur.moc.gui.Screen
import fr.raconteur.moc.gui.components.*

@Composable
fun PatchesTab(state: PatchesState) {
    when (state.view) {
        PatchesView.List    -> PatchListScreen(state)
        PatchesView.Content -> PatchContentScreen(state)
        PatchesView.Recomposition -> RecompositionEditor(state)
    }

    // Dialogs
    if (state.deleteConfirmVisible) {
        val name = state.patches.getOrNull(state.patchIndex)
        DeletePatchDialog(
            patchName = name ?: "",
            onConfirm = {
                if (name != null) {
                    fr.raconteur.moc.versioning.PatchList.delete(name)
                    state.refreshPatches()
                }
                state.deleteConfirmVisible = false
            },
            onDismiss = { state.deleteConfirmVisible = false }
        )
    }

    if (state.recompConflictVisible) {
        RecompConflictDialog(
            onResume = {
                state.recompConflictVisible = false
                state.pendingRecompStart    = null
                state.pendingRecompEnd      = null
                state.resumeRecomposition()
            },
            onOverwrite = {
                val start = state.pendingRecompStart
                val end   = state.pendingRecompEnd
                state.recompConflictVisible = false
                state.pendingRecompStart    = null
                state.pendingRecompEnd      = null
                if (start != null && end != null) state.launchRecomposition(start, end)
            },
            onCancel = {
                state.recompConflictVisible = false
                state.pendingRecompStart    = null
                state.pendingRecompEnd      = null
            }
        )
    }
}

@Composable
private fun RecompositionEditor(state: PatchesState) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalW = constraints.maxWidth.toFloat()
        val totalH = constraints.maxHeight.toFloat()

        var leftFraction by remember { mutableStateOf(0.40f) }
        var topFraction  by remember { mutableStateOf(0.50f) }

        Row(modifier = Modifier.fillMaxSize()) {
            // Left: changes navigation
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(leftFraction)
                    .recompPanelFocusInput { state.recompFocusedPanel = RecompFocusedPanel.Changes }
            ) {
                RecompFocusLine(state.recompFocusedPanel == RecompFocusedPanel.Changes)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (val screen = state.recompScreen) {
                        is Screen.Files -> RecompFilesScreen(state)
                        is Screen.Diff  -> RecompDiffScreen(state)
                        is Screen.Value -> RecompValueScreen(state, screen.returnTo)
                    }
                }
            }

            PanelDivider(horizontal = false) { delta ->
                if (totalW > 0f)
                    leftFraction = (leftFraction + delta / totalW).coerceIn(0.15f, 0.80f)
            }

            // Right: draft (top) + ignores (bottom)
            Column(modifier = Modifier.fillMaxHeight().weight(1f - leftFraction)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(topFraction)
                        .recompPanelFocusInput { state.recompFocusedPanel = RecompFocusedPanel.Draft }
                ) {
                    RecompFocusLine(state.recompFocusedPanel == RecompFocusedPanel.Draft)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        RecompDraftPanel(state)
                    }
                }

                PanelDivider(horizontal = true) { delta ->
                    if (totalH > 0f)
                        topFraction = (topFraction + delta / totalH).coerceIn(0.10f, 0.90f)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f - topFraction)
                        .recompPanelFocusInput { state.recompFocusedPanel = RecompFocusedPanel.Ignores }
                ) {
                    RecompFocusLine(state.recompFocusedPanel == RecompFocusedPanel.Ignores)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        RecompIgnorePanel(state)
                    }
                }
            }
        }

        // Confirm dialog
        state.recompConfirmMessage?.let { msg ->
            ConfirmDialog(
                message   = msg,
                onConfirm = {
                    state.recompConfirmAction?.invoke()
                    state.recompConfirmMessage = null
                    state.recompConfirmAction  = null
                },
                onDismiss = {
                    state.recompConfirmMessage = null
                    state.recompConfirmAction  = null
                }
            )
        }

        if (state.recompFinalizeDialogVisible) {
            RecompFinalizeDialog(state)
        }
    }
}

private fun Modifier.recompPanelFocusInput(onPress: () -> Unit): Modifier =
    pointerInput(onPress) {
        awaitEachGesture {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.pressed && !it.previousPressed }) onPress()
        }
    }

@Composable
private fun RecompFocusLine(focused: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(
                if (focused) MaterialTheme.colors.secondary
                else androidx.compose.ui.graphics.Color.Transparent
            )
    )
}
