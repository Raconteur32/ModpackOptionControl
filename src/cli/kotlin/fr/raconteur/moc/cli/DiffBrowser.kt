package fr.raconteur.moc.cli

import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys
import com.varabyte.kotter.foundation.input.input
import com.varabyte.kotter.foundation.input.onInputChanged
import com.varabyte.kotter.foundation.input.onInputEntered
import com.varabyte.kotter.foundation.input.onKeyPressed
import com.varabyte.kotter.foundation.input.runUntilKeyPressed
import com.varabyte.kotter.foundation.liveVarOf
import com.varabyte.kotter.foundation.session
import com.varabyte.kotter.foundation.text.blue
import com.varabyte.kotter.foundation.text.bold
import com.varabyte.kotter.foundation.text.green
import com.varabyte.kotter.foundation.text.red
import com.varabyte.kotter.foundation.text.text
import com.varabyte.kotter.foundation.text.textLine
import com.varabyte.kotter.foundation.text.yellow
import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.FileDiffKind
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.filesystem.McInstanceRefMocFileSystem
import fr.raconteur.moc.versioning.DraftPatch
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchList
import fr.raconteur.moc.versioning.PatchMode
import java.nio.file.Files
import java.nio.file.Path

private sealed class BrowseMode {
    object Files    : BrowseMode()
    object Diff     : BrowseMode()
    data class Value(val returnTo: BrowseMode) : BrowseMode()
    object Draft    : BrowseMode()
    object Finalize : BrowseMode()
}

private fun openFileIdeDiff(filePath: Path, kind: FileDiffKind) {
    val ext = filePath.toString().substringAfterLast('.', "txt")
    val oldContent = if (kind != FileDiffKind.NEW)
        McInstanceRefMocFileSystem.files.find { it.relativePath == filePath }?.getStringContent() ?: ""
    else ""
    val newContent = if (kind != FileDiffKind.DELETED)
        McInstanceMocFileSystem.files.find { it.relativePath == filePath }?.getStringContent() ?: ""
    else ""
    openIdeDiff(oldContent, newContent, ext)
}

private fun openIdeDiff(oldValue: Any?, newValue: Any?, extension: String) {
    val suffix = if (extension.startsWith('.')) extension else ".$extension"
    val oldFile = Files.createTempFile("moc-old-", suffix).also { it.toFile().writeText(oldValue?.toString() ?: "") }
    val newFile = Files.createTempFile("moc-new-", suffix).also { it.toFile().writeText(newValue?.toString() ?: "") }
    val command = when {
        System.getenv("TERMINAL_EMULATOR")?.contains("JetBrains", ignoreCase = true) == true ->
            listOf("idea", "diff", oldFile.toString(), newFile.toString())
        System.getenv("TERM_PROGRAM")?.equals("vscode", ignoreCase = true) == true ->
            listOf("code", "--diff", oldFile.toString(), newFile.toString())
        else ->
            listOf("idea", "diff", oldFile.toString(), newFile.toString())
    }
    ProcessBuilder(command).inheritIO().start()
}

private fun directChildren(allPaths: List<String>, parent: String): List<String> =
    allPaths.filter { path ->
        if (path == parent || !path.startsWith(parent)) return@filter false
        val suffix = path.removePrefix(parent)
        when {
            suffix.startsWith('.') -> suffix.drop(1).let { !it.contains('.') && !it.contains('[') }
            suffix.startsWith('[') -> suffix.indexOf(']').let { it != -1 && suffix.drop(it + 1).isEmpty() }
            else -> false
        }
    }

private fun isDescendant(childPath: String, parentPath: String): Boolean {
    if (childPath.length <= parentPath.length) return false
    if (!childPath.startsWith(parentPath)) return false
    val c = childPath[parentPath.length]
    return c == '.' || c == '['
}

private fun draftTag(entry: PatchEntry?, hasSub: Boolean): String = when {
    entry?.mode == PatchMode.OVERRIDE -> " [✓ O]"
    entry?.mode == PatchMode.DEFAULT  -> " [✓ D]"
    hasSub                            -> " […]"
    else                              -> ""
}

private fun applyDiffToDraft(optDiff: OptionDiff?, mode: PatchMode) = when (optDiff) {
    is OptionDiff.New     -> DraftPatch.setValueEntry(optDiff, mode)
    is OptionDiff.Changed -> DraftPatch.setValueEntry(optDiff, mode)
    is OptionDiff.Deleted -> DraftPatch.setDeletionEntry(optDiff, mode)
    null                  -> Unit
}

fun runDiffBrowser() {
    session {
        var mode           by liveVarOf<BrowseMode>(BrowseMode.Files)
        var fileIndex      by liveVarOf(0)
        var diffIndex      by liveVarOf(0)
        var draftIndex     by liveVarOf(0)
        var pathStack      by liveVarOf(listOf("$"))
        var valuePath      by liveVarOf<String?>(null)
        var draftEntries   by liveVarOf(DraftPatch.entries.toList())
        var patchName      by liveVarOf("")
        var patchNameError by liveVarOf<String?>(null)
        var confirmMessage by liveVarOf<String?>(null)
        var confirmAction  by liveVarOf<(() -> Unit)?>(null)
        var entries        by liveVarOf(McInstanceMocFileSystem.diffFrom(McInstanceRefMocFileSystem).entries.sortedBy { it.key.toString() })

        fun draftFileEntry(filePath: Path): PatchEntry? =
            draftEntries.firstOrNull { it.filePath == filePath.toString() && (it.optionPath == "$" || it.optionPath == "") }

        fun draftForOption(filePath: Path, optionPath: String): PatchEntry? =
            draftEntries.find { it.filePath == filePath.toString() && it.optionPath == optionPath }

        fun hasSubDraft(filePath: Path, parentPath: String): Boolean =
            draftEntries.any { it.filePath == filePath.toString() && isDescendant(it.optionPath, parentPath) }

        fun applyOptionEntry(filePath: Path, optionPath: String, doApply: () -> Unit) {
            val parentEntry = draftEntries.firstOrNull {
                it.filePath == filePath.toString() && isDescendant(optionPath, it.optionPath)
            }
            if (parentEntry != null) {
                confirmMessage = "Parent entry « ${parentEntry.optionPath} » [${parentEntry.mode}] will be removed."
                confirmAction  = {
                    DraftPatch.removeEntry(parentEntry.filePath, parentEntry.optionPath)
                    doApply()
                    draftEntries = DraftPatch.entries.toList()
                }
                return
            }
            val children = draftEntries.filter {
                it.filePath == filePath.toString() && isDescendant(it.optionPath, optionPath)
            }
            if (children.isNotEmpty()) {
                confirmMessage = "${children.size} sub-entr${if (children.size > 1) "ies" else "y"} will be removed."
                confirmAction  = {
                    children.forEach { DraftPatch.removeEntry(it.filePath, it.optionPath) }
                    doApply()
                    draftEntries = DraftPatch.entries.toList()
                }
                return
            }
            doApply()
            draftEntries = DraftPatch.entries.toList()
        }

        // ── Gestionnaires de touches ────────────────────────────────────────────

        fun handleFilesKey(k: Key) {
            when (k) {
                Keys.UP      -> if (fileIndex > 0) fileIndex--
                Keys.DOWN    -> if (fileIndex < entries.size - 1) fileIndex++
                CharKey('i') -> openFileIdeDiff(entries[fileIndex].key, entries[fileIndex].value.kind)
                CharKey('e') -> if (draftEntries.isNotEmpty()) { draftIndex = 0; mode = BrowseMode.Draft }
                CharKey('f') -> if (draftEntries.isNotEmpty()) mode = BrowseMode.Finalize
                CharKey('d') -> {
                    val (filePath, fileDiff) = entries[fileIndex]
                    if (draftFileEntry(filePath) == null) {
                        val optDiff = if (fileDiff.kind == FileDiffKind.DELETED)
                            fileDiff.flatContentDiff[""] else fileDiff.flatContentDiff["$"]
                        if (optDiff != null) applyOptionEntry(filePath, optDiff.path) {
                            applyDiffToDraft(optDiff, PatchMode.DEFAULT)
                        }
                    }
                }
                CharKey('o') -> {
                    val (filePath, fileDiff) = entries[fileIndex]
                    if (draftFileEntry(filePath) == null) {
                        val optDiff = if (fileDiff.kind == FileDiffKind.DELETED)
                            fileDiff.flatContentDiff[""] else fileDiff.flatContentDiff["$"]
                        if (optDiff != null) applyOptionEntry(filePath, optDiff.path) {
                            applyDiffToDraft(optDiff, PatchMode.OVERRIDE)
                        }
                    }
                }
                CharKey('r') -> {
                    val (filePath, _) = entries[fileIndex]
                    val fileEntry = draftFileEntry(filePath)
                    if (fileEntry != null) {
                        DraftPatch.removeEntry(filePath.toString(), fileEntry.optionPath)
                        draftEntries = DraftPatch.entries.toList()
                    }
                }
                Keys.ENTER -> {
                    val fileDiff = entries[fileIndex].value
                    val allPaths = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
                    if (directChildren(allPaths, "$").isNotEmpty()) {
                        pathStack = listOf("$")
                        diffIndex = 0
                        mode = BrowseMode.Diff
                    } else {
                        valuePath = "$"
                        mode = BrowseMode.Value(returnTo = BrowseMode.Files)
                    }
                }
            }
        }

        fun handleDiffKey(k: Key) {
            val allPaths = entries[fileIndex].value.flatContentDiff.keys.filter { it != "$" }.toList()
            val visible  = directChildren(allPaths, pathStack.last())
            when (k) {
                CharKey('i') -> openFileIdeDiff(entries[fileIndex].key, entries[fileIndex].value.kind)
                Keys.UP      -> if (diffIndex > 0) diffIndex--
                Keys.DOWN    -> if (diffIndex < visible.size - 1) diffIndex++
                Keys.ENTER   -> {
                    if (visible.isNotEmpty()) {
                        val selected = visible[diffIndex]
                        if (directChildren(allPaths, selected).isNotEmpty()) {
                            pathStack = pathStack + selected
                            diffIndex = 0
                        } else {
                            valuePath = selected
                            mode = BrowseMode.Value(returnTo = BrowseMode.Diff)
                        }
                    }
                }
                CharKey('d') -> if (visible.isNotEmpty()) {
                    val (filePath, fileDiff) = entries[fileIndex]
                    val selected = visible[diffIndex]
                    val optDiff  = fileDiff.flatContentDiff[selected]
                    if (draftForOption(filePath, selected) == null && optDiff != null)
                        applyOptionEntry(filePath, selected) { applyDiffToDraft(optDiff, PatchMode.DEFAULT) }
                }
                CharKey('o') -> if (visible.isNotEmpty()) {
                    val (filePath, fileDiff) = entries[fileIndex]
                    val selected = visible[diffIndex]
                    val optDiff  = fileDiff.flatContentDiff[selected]
                    if (draftForOption(filePath, selected) == null && optDiff != null)
                        applyOptionEntry(filePath, selected) { applyDiffToDraft(optDiff, PatchMode.OVERRIDE) }
                }
                CharKey('r') -> if (visible.isNotEmpty()) {
                    val (filePath, _) = entries[fileIndex]
                    val selected = visible[diffIndex]
                    if (draftForOption(filePath, selected) != null) {
                        DraftPatch.removeEntry(filePath.toString(), selected)
                        draftEntries = DraftPatch.entries.toList()
                    }
                }
                Keys.ESC -> {
                    if (pathStack.size > 1) {
                        pathStack = pathStack.dropLast(1)
                        diffIndex = 0
                    } else {
                        mode = BrowseMode.Files
                    }
                }
            }
        }

        fun handleValueKey(k: Key, returnTo: BrowseMode) {
            val (filePath, fileDiff) = entries[fileIndex]
            val optDiff = valuePath?.let { fileDiff.flatContentDiff[it] }
            val vp      = valuePath ?: return
            val inDraft = draftForOption(filePath, vp)
            when (k) {
                Keys.ESC     -> mode = returnTo
                CharKey('i') -> if (optDiff is OptionDiff.Changed) {
                    val ext = filePath.toString().substringAfterLast('.', "txt")
                    openIdeDiff(optDiff.oldValue, optDiff.newValue, ext)
                }
                CharKey('r') -> if (inDraft != null) {
                    DraftPatch.removeEntry(filePath.toString(), vp)
                    draftEntries = DraftPatch.entries.toList()
                }
                CharKey('d') -> if (inDraft == null && optDiff != null)
                    applyOptionEntry(filePath, vp) { applyDiffToDraft(optDiff, PatchMode.DEFAULT) }
                CharKey('o') -> if (inDraft == null && optDiff != null)
                    applyOptionEntry(filePath, vp) { applyDiffToDraft(optDiff, PatchMode.OVERRIDE) }
            }
        }

        fun handleDraftKey(k: Key) {
            when (k) {
                Keys.UP      -> if (draftIndex > 0) draftIndex--
                Keys.DOWN    -> if (draftIndex < draftEntries.size - 1) draftIndex++
                CharKey('r') -> if (draftEntries.isNotEmpty()) {
                    val entry = draftEntries[draftIndex]
                    DraftPatch.removeEntry(entry.filePath, entry.optionPath)
                    draftEntries = DraftPatch.entries.toList()
                    if (draftIndex >= draftEntries.size && draftIndex > 0) draftIndex--
                }
                CharKey('f') -> if (draftEntries.isNotEmpty()) mode = BrowseMode.Finalize
                Keys.ESC     -> mode = BrowseMode.Files
            }
        }

        fun handleFinalizeKey(k: Key) {
            when (k) {
                Keys.ESC -> { patchName = ""; mode = BrowseMode.Files }
                else     -> Unit
            }
        }

        // ── Rendu ──────────────────────────────────────────────────────────────

        section {
            fun renderBreadcrumb() {
                when (mode) {
                    is BrowseMode.Files -> {
                        bold { text("FILES") }
                        textLine("  │  ${entries.size} file(s) changed · draft: ${draftEntries.size}")
                    }
                    is BrowseMode.Diff -> {
                        val (filePath, _) = entries[fileIndex]
                        bold { text("DIFF") }
                        textLine("  │  $filePath  ·  ${pathStack.joinToString(" › ")}")
                    }
                    is BrowseMode.Value -> {
                        val (filePath, _) = entries[fileIndex]
                        bold { text("VALUE") }
                        textLine("  │  $filePath  ·  ${valuePath ?: ""}")
                    }
                    is BrowseMode.Draft -> {
                        bold { text("DRAFT") }
                        textLine("  │  ${draftEntries.size} pending entr${if (draftEntries.size > 1) "ies" else "y"}")
                    }
                    is BrowseMode.Finalize -> {
                        bold { text("FINALIZE") }
                        textLine("  │  ${draftEntries.size} pending entr${if (draftEntries.size > 1) "ies" else "y"}")
                    }
                }
                textLine()
            }

            fun renderLegend() {
                green { text("+ new") }; text("   "); red { text("- deleted") }; text("   "); yellow { textLine("~ changed") }
            }

            fun renderFiles() {
                val draftCount = draftEntries.size
                entries.forEachIndexed { i, (path, fileDiff) ->
                    val cursor = if (i == fileIndex) "> " else "  "
                    val arrow  = if (fileDiff.flatContentDiff.isNotEmpty()) " ▶" else ""
                    val tag    = draftTag(draftFileEntry(path), hasSubDraft(path, "$"))
                    when (fileDiff.kind) {
                        FileDiffKind.NEW     -> green  { text(cursor); bold { text("+ $path$arrow") }; blue { textLine(tag) } }
                        FileDiffKind.DELETED -> red    { text(cursor); bold { text("- $path$arrow") }; blue { textLine(tag) } }
                        FileDiffKind.CHANGED -> yellow { text(cursor); bold { text("~ $path$arrow") }; blue { textLine(tag) } }
                    }
                }
                textLine()
                renderLegend()
                val actionHint = if (entries.isNotEmpty()) {
                    val (filePath, _) = entries[fileIndex]
                    if (draftFileEntry(filePath) != null) "r remove" else "d default   o override"
                } else ""
                val hints = buildList {
                    add("↑↓ navigate"); add("↵ open"); add("i diff IDE")
                    if (actionHint.isNotEmpty()) add(actionHint)
                    if (draftCount > 0) { add("e entries"); add("f finalize") }
                    add("q quit")
                }
                textLine(hints.joinToString("   "))
            }

            fun renderDiff() {
                val (filePath, fileDiff) = entries[fileIndex]
                val allPaths      = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
                val currentParent = pathStack.last()
                val visible       = directChildren(allPaths, currentParent)
                when (fileDiff.kind) {
                    FileDiffKind.NEW     -> green  { bold { textLine("+ $filePath") } }
                    FileDiffKind.DELETED -> red    { bold { textLine("- $filePath") } }
                    FileDiffKind.CHANGED -> yellow { bold { textLine("~ $filePath") } }
                }
                textLine()
                if (visible.isEmpty()) {
                    textLine("  (no sub-items)")
                } else {
                    visible.forEachIndexed { i, path ->
                        val cursor      = if (i == diffIndex) "> " else "  "
                        val entry       = fileDiff.flatContentDiff[path]
                        val hasChildren = directChildren(allPaths, path).isNotEmpty()
                        val arrow       = if (hasChildren) " ▶" else ""
                        val tag         = draftTag(draftForOption(filePath, path), hasSubDraft(filePath, path))
                        when (entry) {
                            is OptionDiff.New     -> green  { text("$cursor+ $path$arrow"); blue { textLine(tag) } }
                            is OptionDiff.Deleted -> red    { text("$cursor- $path$arrow"); blue { textLine(tag) } }
                            is OptionDiff.Changed -> yellow { text("$cursor~ $path$arrow"); blue { textLine(tag) } }
                            null                  -> textLine("$cursor? $path$arrow")
                        }
                    }
                }
                textLine()
                renderLegend()
                val actionHint = if (visible.isNotEmpty()) {
                    val selected = visible[diffIndex]
                    if (draftForOption(filePath, selected) != null) "r remove" else "d default   o override"
                } else ""
                val escHint = if (pathStack.size > 1) "esc up" else "esc back"
                val hints = buildList {
                    add("↑↓ navigate"); add("↵ enter"); add("i diff IDE")
                    if (actionHint.isNotEmpty()) add(actionHint)
                    add(escHint); add("q quit")
                }
                textLine(hints.joinToString("   "))
            }

            fun renderValue() {
                val (filePath, fileDiff) = entries[fileIndex]
                val optDiff = valuePath?.let { fileDiff.flatContentDiff[it] }
                val inDraft = valuePath?.let { draftForOption(filePath, it) }
                fun preview(v: Any?) = v?.toString()?.replace("\n", "↵")?.take(120)?.let {
                    if (v.toString().length > 120) "$it…" else it
                } ?: "null"
                bold { text(valuePath ?: "") }
                if (inDraft != null) blue { textLine("  [✓ ${inDraft.mode}]") } else textLine()
                textLine()
                when (optDiff) {
                    is OptionDiff.New -> {
                        green { textLine("+ New") }
                        textLine()
                        textLine("  Value: ${preview(optDiff.newValue)}")
                    }
                    is OptionDiff.Deleted -> {
                        red { textLine("- Deleted") }
                        textLine()
                        textLine("  Old value: ${preview(optDiff.oldValue)}")
                    }
                    is OptionDiff.Changed -> {
                        red   { textLine("  Before: ${preview(optDiff.oldValue)}") }
                        green { textLine("  After:  ${preview(optDiff.newValue)}") }
                    }
                    null -> textLine("  (unknown value)")
                }
                textLine()
                val hints = buildList {
                    if (optDiff is OptionDiff.Changed) add("i diff IDE")
                    if (inDraft != null) add("r remove") else { add("d default"); add("o override") }
                    add("esc back"); add("q quit")
                }
                textLine(hints.joinToString("   "))
            }

            fun renderDraft() {
                draftEntries.forEachIndexed { i, entry ->
                    val cursor  = if (i == draftIndex) "> " else "  "
                    val modeTag = if (entry.mode == PatchMode.OVERRIDE) "[✓ OVERRIDE]" else "[✓ DEFAULT] "
                    text(cursor)
                    text("${entry.filePath}  ·  ${entry.optionPath}")
                    blue { textLine("  $modeTag") }
                }
                textLine()
                val hints = buildList {
                    add("↑↓ navigate"); add("r remove")
                    if (draftEntries.isNotEmpty()) add("f finalize")
                    add("esc back"); add("q quit")
                }
                textLine(hints.joinToString("   "))
            }

            fun renderFinalize() {
                text("Name: "); input()
                textLine()
                if (patchNameError != null) {
                    red { textLine(patchNameError!!) }
                } else {
                    textLine()
                }
                val hints = buildList {
                    if (patchNameError == null && patchName.isNotBlank()) add("↵ confirm")
                    add("esc cancel")
                }
                textLine(hints.joinToString("   "))
            }

            renderBreadcrumb()
            when (val m = mode) {
                is BrowseMode.Files    -> renderFiles()
                is BrowseMode.Diff     -> renderDiff()
                is BrowseMode.Value    -> renderValue()
                is BrowseMode.Draft    -> renderDraft()
                is BrowseMode.Finalize -> renderFinalize()
            }
            if (confirmMessage != null) {
                textLine()
                yellow { bold { textLine("⚠  ${confirmMessage}") } }
                textLine("↵ confirm   esc cancel")
            }
        }.runUntilKeyPressed(Keys.Q) {
            onInputChanged {
                patchName = input
                patchNameError = if (input.isNotBlank() && PatchList.contains(input))
                    "« $input » is already taken"
                else null
            }
            onInputEntered {
                if (mode is BrowseMode.Finalize && patchName.isNotBlank() && patchNameError == null) {
                    DraftPatch.finalize(patchName)
                    entries      = McInstanceMocFileSystem.diffFrom(McInstanceRefMocFileSystem).entries.sortedBy { it.key.toString() }
                    fileIndex    = 0
                    diffIndex    = 0
                    pathStack    = listOf("$")
                    draftEntries = DraftPatch.entries.toList()
                    patchName    = ""
                    mode         = BrowseMode.Files
                }
            }
            onKeyPressed {
                if (confirmMessage != null) {
                    when (key) {
                        Keys.ENTER -> { confirmAction?.invoke(); confirmMessage = null; confirmAction = null }
                        Keys.ESC   -> { confirmMessage = null; confirmAction = null }
                        else -> Unit
                    }
                    return@onKeyPressed
                }
                when (val m = mode) {
                    is BrowseMode.Files    -> handleFilesKey(key)
                    is BrowseMode.Diff     -> handleDiffKey(key)
                    is BrowseMode.Value    -> handleValueKey(key, m.returnTo)
                    is BrowseMode.Draft    -> handleDraftKey(key)
                    is BrowseMode.Finalize -> handleFinalizeKey(key)
                }
            }
        }
    }
}
