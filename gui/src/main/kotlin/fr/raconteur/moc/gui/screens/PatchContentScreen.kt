package fr.raconteur.moc.gui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.raconteur.moc.gui.PatchesState
import fr.raconteur.moc.gui.PatchesView
import fr.raconteur.moc.gui.components.DraftBadge
import fr.raconteur.moc.versioning.Patch
import fr.raconteur.moc.versioning.PatchMode

@Composable
fun PatchContentScreen(state: PatchesState) {
    val name = state.patches.getOrNull(state.patchIndex) ?: run {
        state.view = PatchesView.List; return
    }
    val patch = try { Patch.load(name) } catch (_: Exception) { null }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { state.view = PatchesView.List },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) { Text("←  Back", fontSize = 12.sp) }
            Spacer(Modifier.width(12.dp))
            Text(name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(12.dp))

        if (patch == null) {
            Text("Could not load patch.", color = MaterialTheme.colors.error)
            return
        }

        Text(
            "${patch.entries.size} entr${if (patch.entries.size != 1) "ies" else "y"}",
            color = Color.Gray, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))

        if (patch.entries.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Empty patch.", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(patch.entries) { _, entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(entry.filePath) }
                                append("  ")
                                withStyle(SpanStyle(color = Color.Gray, fontSize = 10.sp)) { append(entry.optionPath) }
                            },
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        DraftBadge(if (entry.mode == PatchMode.OVERRIDE) "[O]" else "[D]")
                    }
                    Divider(color = Color.Gray.copy(alpha = 0.12f), thickness = 0.5.dp)
                }
            }
        }
    }
}
