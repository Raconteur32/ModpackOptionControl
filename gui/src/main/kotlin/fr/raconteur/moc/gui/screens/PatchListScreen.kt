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
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.raconteur.moc.gui.PatchesState
import fr.raconteur.moc.gui.PatchesView
import fr.raconteur.moc.versioning.RecompositionDraft

@Composable
fun PatchListScreen(state: PatchesState) {
    val patches   = state.patches
    val listState = rememberLazyListState()

    LaunchedEffect(state.patchIndex) {
        if (patches.isNotEmpty())
            listState.scrollToItem(state.patchIndex.coerceAtMost(patches.size - 1))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PATCHES", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(12.dp))
            Text("${patches.size} patch${if (patches.size != 1) "es" else ""}", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            if (RecompositionDraft.hasActiveDraft()) {
                Button(
                    onClick = { state.resumeRecomposition() },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("Resume recomposition", fontSize = 11.sp) }
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        if (patches.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No patches yet.", color = Color.Gray)
            }
        } else {
            val range = state.selectedRange
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(patches) { i, name ->
                    val isSelected    = i == state.patchIndex
                    val isInRange     = range != null && i in range
                    val bgColor = when {
                        isInRange  -> MaterialTheme.colors.primary.copy(alpha = 0.18f)
                        isSelected -> MaterialTheme.colors.primary.copy(alpha = 0.10f)
                        else       -> Color.Transparent
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .pointerInput(i) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: continue
                                        if (change.pressed && !change.previousPressed) {
                                            if (event.keyboardModifiers.isShiftPressed && state.rangeAnchor != null) {
                                                state.patchIndex = i
                                            } else {
                                                state.rangeAnchor = i
                                                state.patchIndex  = i
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${i + 1}.",
                            color = Color.Gray, fontSize = 11.sp,
                            modifier = Modifier.width(30.dp)
                        )
                        Text(name, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                    Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 0.5.dp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (patches.isNotEmpty()) {
                OutlinedButton(onClick = { state.view = PatchesView.Content }) { Text("↵  View") }
                OutlinedButton(onClick = { state.deleteConfirmVisible = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.error)
                ) { Text("Del  Delete") }
            }
            val range = state.selectedRange
            if (range != null && range.first != range.last) {
                Spacer(Modifier.width(4.dp))
                Button(onClick = { state.tryLaunchRecomposition(range.first, range.last) }) {
                    Text("R  Recompose (${range.last - range.first + 1} patches)", fontSize = 11.sp)
                }
            } else if (patches.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { state.tryLaunchRecomposition(state.patchIndex, state.patchIndex) }) {
                    Text("R  Recompose", fontSize = 11.sp)
                }
            }
        }
    }
}
