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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.raconteur.moc.gui.AppState
import fr.raconteur.moc.gui.IgnoreEntry
import fr.raconteur.moc.gui.IgnoreKind

@Composable
fun IgnoreScreen(state: AppState, kind: IgnoreKind, entries: List<IgnoreEntry>) {
    val listState = rememberLazyListState()
    val title = when (kind) {
        IgnoreKind.Session   -> "SKIP · PATCH"
        IgnoreKind.Value     -> "SKIP · VALUE"
        IgnoreKind.Permanent -> "SKIP · ALWAYS"
    }
    val subtitle = when (kind) {
        IgnoreKind.Session   -> "Reset when a patch is finalized"
        IgnoreKind.Value     -> "Lifted when the target value changes"
        IgnoreKind.Permanent -> "Ignored indefinitely"
    }
    val totalCount = when (kind) {
        IgnoreKind.Session   -> state.ignoreSessionEntries.size
        IgnoreKind.Value     -> state.ignoreValueEntries.size
        IgnoreKind.Permanent -> state.ignorePermanentEntries.size
    }
    val countText = if (state.ignoreSearch.isBlank()) {
        "${entries.size} entr${if (entries.size != 1) "ies" else "y"}"
    } else {
        "${entries.size} / $totalCount entr${if (totalCount != 1) "ies" else "y"}"
    }

    LaunchedEffect(state.ignoreIndex) {
        if (entries.isNotEmpty())
            listState.scrollToItem(state.ignoreIndex.coerceAtMost(entries.size - 1))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(12.dp))
            Text("$countText — $subtitle", color = Color.Gray, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.ignoreSearch,
            onValueChange = { state.ignoreSearch = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state.ignoreSearchFocused = it.isFocused },
            placeholder = { Text("Search by file or option…", fontSize = 13.sp) },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        if (entries.isEmpty()) {
            Text(
                if (state.ignoreSearch.isBlank()) "No ignored entries." else "No entries match the search.",
                color = Color.Gray, modifier = Modifier.padding(8.dp)
            )
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(entries) { i, entry ->
                    val selected = i == state.ignoreIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { state.ignoreIndex = i }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.filePath, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                                fontWeight = FontWeight.Medium)
                            Text(entry.optionPath, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                color = Color.Gray)
                            if (entry.targetValue != null) {
                                Text("→ ${entry.targetValue.take(80)}", fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp, color = Color(0xFF888888))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { state.removeCurrentIgnoreEntry() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("R  Remove", fontSize = 12.sp) }
                    }
                    Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 0.5.dp)
                }
            }
        }
    }
}
