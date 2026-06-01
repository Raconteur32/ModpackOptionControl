package fr.raconteur.moc.gui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.raconteur.moc.gui.components.ConfirmDialog
import fr.raconteur.moc.gui.screens.*

@Composable
fun App() {
    val state = remember { AppState() }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                when (val screen = state.screen) {
                    is Screen.Files    -> FilesScreen(state)
                    is Screen.Diff     -> DiffScreen(state)
                    is Screen.Value    -> ValueScreen(state, screen.returnTo)
                    is Screen.Draft    -> DraftScreen(state)
                    is Screen.Finalize -> FinalizeScreen(state)
                }
            }

            state.confirmMessage?.let { msg ->
                ConfirmDialog(
                    message = msg,
                    onConfirm = { state.confirmAction?.invoke(); state.confirmMessage = null; state.confirmAction = null },
                    onDismiss = { state.confirmMessage = null; state.confirmAction = null }
                )
            }
        }
    }
}
