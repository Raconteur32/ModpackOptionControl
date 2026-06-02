package fr.raconteur.moc.gui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.raconteur.moc.gui.components.ConfirmDialog
import fr.raconteur.moc.gui.screens.*

@Composable
fun App(state: AppState) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = AppTab.entries.indexOf(state.currentTab)) {
                    AppTab.entries.forEach { tab ->
                        Tab(
                            selected  = tab == state.currentTab,
                            onClick   = { state.switchTab(tab) },
                            text      = { Text(tab.label) }
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f).padding(4.dp)) {
                    when (val screen = state.screen) {
                        is Screen.Files    -> FilesScreen(state)
                        is Screen.Diff     -> DiffScreen(state)
                        is Screen.Value    -> ValueScreen(state, screen.returnTo)
                        is Screen.Draft    -> DraftScreen(state)
                        is Screen.Finalize -> FinalizeScreen(state)
                    }
                }
            }

            state.confirmMessage?.let { msg ->
                ConfirmDialog(
                    message   = msg,
                    onConfirm = { state.confirmAction?.invoke(); state.confirmMessage = null; state.confirmAction = null },
                    onDismiss = { state.confirmMessage = null; state.confirmAction = null }
                )
            }
        }
    }
}
