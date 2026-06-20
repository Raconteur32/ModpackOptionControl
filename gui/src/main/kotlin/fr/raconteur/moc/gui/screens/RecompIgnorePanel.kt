package fr.raconteur.moc.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.raconteur.moc.gui.PatchesState
import fr.raconteur.moc.gui.RecompFocusedPanel

@Composable
fun RecompIgnorePanel(state: PatchesState) {
    val ignores   = state.recompIgnores
    val listState = rememberLazyListState()

    LaunchedEffect(state.recompIgnoreIndex) {
        if (ignores.isNotEmpty())
            listState.scrollToItem(state.recompIgnoreIndex.coerceAtMost(ignores.size - 1))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("IGNORES", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "${ignores.size} entr${if (ignores.size != 1) "ies" else "y"}",
                color = Color.Gray, fontSize = 11.sp
            )
        }
        Divider(color = Color.Gray.copy(alpha = 0.20f), thickness = 1.dp)

        if (ignores.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No ignored entries.", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(ignores) { i, entry ->
                    val selected = state.recompFocusedPanel == RecompFocusedPanel.Ignores && i == state.recompIgnoreIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) MaterialTheme.colors.primary.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable { state.recompIgnoreIndex = i }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.filePath,
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp
                            )
                            if (entry.optionPath.isNotEmpty()) {
                                Text(
                                    entry.optionPath,
                                    fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                state.recompIgnoreIndex = i
                                state.recompRemoveCurrentIgnore()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("R", fontSize = 11.sp) }
                    }
                    Divider(color = Color.Gray.copy(alpha = 0.12f), thickness = 0.5.dp)
                }
            }
        }
    }
}
