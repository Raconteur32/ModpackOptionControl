package fr.raconteur.moc.gui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.raconteur.moc.gui.AppState
import fr.raconteur.moc.gui.FocusedPanel
import fr.raconteur.moc.gui.IgnoreEntry
import fr.raconteur.moc.gui.IgnoreFilter
import fr.raconteur.moc.gui.IgnoreKind

private fun kindLabel(kind: IgnoreKind): String = when (kind) {
    IgnoreKind.Session   -> "patch"
    IgnoreKind.Value     -> "value"
    IgnoreKind.Permanent -> "perm"
    IgnoreKind.Directory -> "dir"
}

@Composable
fun IgnorePanel(state: AppState) {
    val listState = rememberLazyListState()

    val entriesWithKind = state.currentIgnoreEntriesWithKind()
    val totalCount = when (state.ignoreFilter) {
        IgnoreFilter.All       -> state.ignoreSessionEntries.size + state.ignoreValueEntries.size + state.ignorePermanentEntries.size + state.ignoredDirectories.size
        IgnoreFilter.Session   -> state.ignoreSessionEntries.size
        IgnoreFilter.Value     -> state.ignoreValueEntries.size
        IgnoreFilter.Permanent -> state.ignorePermanentEntries.size
        IgnoreFilter.Directory -> state.ignoredDirectories.size
    }
    val shownCount = if (state.ignoreFilter == IgnoreFilter.Directory) totalCount else entriesWithKind.size

    LaunchedEffect(state.ignoreIndex) {
        val max = if (state.ignoreFilter == IgnoreFilter.Directory)
            state.ignoredDirectories.size else entriesWithKind.size
        if (max > 0)
            listState.scrollToItem(state.ignoreIndex.coerceAtMost(max - 1))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("IGNORES", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))

            // Filter dropdown
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("${state.ignoreFilter.label} ▾", fontSize = 11.sp)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    IgnoreFilter.entries.forEach { filter ->
                        DropdownMenuItem(onClick = {
                            state.ignoreFilter = filter
                            state.ignoreIndex  = 0
                            expanded = false
                        }) {
                            Text(filter.label, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            val countText = if (state.ignoreFilter != IgnoreFilter.Directory && state.ignoreSearch.isNotBlank())
                "$shownCount / $totalCount" else "$shownCount"
            Text("$countText entr${if (totalCount != 1) "ies" else "y"}", color = Color.Gray, fontSize = 11.sp)
        }
        Divider(color = Color.Gray.copy(alpha = 0.20f), thickness = 1.dp)

        // Search field (hidden for Directory filter)
        if (state.ignoreFilter != IgnoreFilter.Directory) {
            OutlinedTextField(
                value         = state.ignoreSearch,
                onValueChange = { state.ignoreSearch = it },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .onFocusChanged { state.ignoreSearchFocused = it.isFocused },
                placeholder   = { Text("Search by file or option…", fontSize = 11.sp) },
                singleLine    = true
            )
        }

        // Content
        if (state.ignoreFilter == IgnoreFilter.Directory) {
            DirectoryIgnoreList(state, listState)
        } else {
            IgnoreEntryList(state, listState, entriesWithKind)
        }
    }
}

@Composable
private fun DirectoryIgnoreList(state: AppState, listState: androidx.compose.foundation.lazy.LazyListState) {
    val dirs = state.ignoredDirectories
    if (dirs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No ignored directories.", color = Color.Gray, fontSize = 12.sp)
        }
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(dirs) { i, path ->
            val selected = state.focusedPanel == FocusedPanel.Ignores && i == state.ignoreIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (selected) MaterialTheme.colors.primary.copy(alpha = 0.10f)
                        else Color.Transparent
                    )
                    .clickable { state.ignoreIndex = i }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    path.toString(),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    modifier = Modifier.weight(1f), maxLines = 1
                )
                OutlinedButton(
                    onClick = { state.removeIgnoredDirectory(path) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("R", fontSize = 11.sp) }
            }
            Divider(color = Color.Gray.copy(alpha = 0.12f), thickness = 0.5.dp)
        }
    }
}

@Composable
private fun IgnoreEntryList(
    state: AppState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    entries: List<Pair<IgnoreEntry, IgnoreKind>>
) {
    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (state.ignoreSearch.isBlank()) "No ignored entries." else "No entries match the search.",
                color = Color.Gray, fontSize = 12.sp
            )
        }
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(entries) { i, (entry, kind) ->
            val selected = state.focusedPanel == FocusedPanel.Ignores && i == state.ignoreIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (selected) MaterialTheme.colors.primary.copy(alpha = 0.10f)
                        else Color.Transparent
                    )
                    .clickable { state.ignoreIndex = i }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        buildAnnotatedString {
                            if (state.ignoreFilter == IgnoreFilter.All) {
                                withStyle(SpanStyle(color = Color(0xFF888888), fontSize = 9.sp)) {
                                    append("[${kindLabel(kind)}]  ")
                                }
                            }
                            append(entry.filePath)
                            if (kind != IgnoreKind.Directory && entry.optionPath.isNotEmpty()) {
                                append("  ")
                                withStyle(SpanStyle(color = Color.Gray, fontSize = 10.sp)) {
                                    append(entry.optionPath)
                                }
                            }
                        },
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp
                    )
                    if (entry.targetValue != null) {
                        Text(
                            "→ ${entry.targetValue.take(60)}",
                            fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                            color = Color(0xFF888888), maxLines = 1
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        state.ignoreIndex = i
                        state.removeCurrentIgnoreEntry()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("R", fontSize = 11.sp) }
            }
            Divider(color = Color.Gray.copy(alpha = 0.12f), thickness = 0.5.dp)
        }
    }
}
