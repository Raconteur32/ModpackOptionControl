package fr.raconteur.moc.cli

import com.varabyte.kotter.foundation.input.CharKey
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
import fr.raconteur.moc.lua.ScriptUtils
import fr.raconteur.moc.versioning.DraftPatch
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchMode
import java.nio.file.Files
import java.nio.file.Path

private enum class BrowseMode { FILES, DIFF, VALUE, FINALIZE }

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

private fun draftTag(entry: PatchEntry?): String = when (entry?.mode) {
    PatchMode.OVERRIDE -> " [✓ O]"
    PatchMode.DEFAULT  -> " [✓ D]"
    null               -> ""
}

fun runDiffBrowser() {
    ScriptUtils.ensureScripts()
    val diff = McInstanceMocFileSystem.diffFrom(McInstanceRefMocFileSystem)
    val entries = diff.entries.sortedBy { it.key.toString() }

    session {
        var mode          by liveVarOf(BrowseMode.FILES)
        var previousMode  by liveVarOf(BrowseMode.FILES)
        var fileIndex     by liveVarOf(0)
        var diffIndex     by liveVarOf(0)
        var pathStack     by liveVarOf(listOf("$"))
        var valuePath     by liveVarOf<String?>(null)
        var draftEntries  by liveVarOf(DraftPatch.entries.toList())
        var patchName     by liveVarOf("")

        fun draftForFile(filePath: Path) = draftEntries.firstOrNull { it.filePath == filePath.toString() && it.optionPath == "$" }
            ?: draftEntries.firstOrNull { it.filePath == filePath.toString() }
        fun draftForOption(filePath: Path, optionPath: String) = draftEntries.find { it.filePath == filePath.toString() && it.optionPath == optionPath }

        section {
            when (mode) {
                BrowseMode.FILES -> {
                    val draftCount = draftEntries.size
                    textLine("Diff — ${entries.size} fichier(s) modifié(s)   Draft: $draftCount")
                    textLine()
                    entries.forEachIndexed { i, (path, fileDiff) ->
                        val cursor = if (i == fileIndex) "> " else "  "
                        val arrow = if (fileDiff.flatContentDiff.isNotEmpty()) " ▶" else ""
                        val tag = draftTag(draftForFile(path))
                        when (fileDiff.kind) {
                            FileDiffKind.NEW     -> green  { text(cursor); bold { text("+ $path$arrow") }; blue { textLine(tag) } }
                            FileDiffKind.DELETED -> red    { text(cursor); bold { text("- $path$arrow") }; blue { textLine(tag) } }
                            FileDiffKind.CHANGED -> yellow { text(cursor); bold { text("~ $path$arrow") }; blue { textLine(tag) } }
                        }
                    }
                    textLine()
                    green { text("+ nouveau") }; text("   "); red { text("- supprimé") }; text("   "); yellow { textLine("~ modifié") }
                    if (draftCount > 0)
                        textLine("↑↓ naviguer   ↵ ouvrir   i diff IDE   f finaliser   q quitter")
                    else
                        textLine("↑↓ naviguer   ↵ ouvrir   i diff IDE   q quitter")
                }

                BrowseMode.DIFF -> {
                    val (filePath, fileDiff) = entries[fileIndex]
                    val allPaths = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
                    val currentParent = pathStack.last()
                    val visible = directChildren(allPaths, currentParent)

                    when (fileDiff.kind) {
                        FileDiffKind.NEW     -> green  { bold { textLine("+ $filePath") } }
                        FileDiffKind.DELETED -> red    { bold { textLine("- $filePath") } }
                        FileDiffKind.CHANGED -> yellow { bold { textLine("~ $filePath") } }
                    }
                    textLine("  ${pathStack.joinToString(" › ")}")
                    textLine()

                    if (visible.isEmpty()) {
                        textLine("  (aucun sous-élément)")
                    } else {
                        visible.forEachIndexed { i, path ->
                            val cursor = if (i == diffIndex) "> " else "  "
                            val entry = fileDiff.flatContentDiff[path]
                            val hasChildren = directChildren(allPaths, path).isNotEmpty()
                            val arrow = if (hasChildren) " ▶" else ""
                            val tag = draftTag(draftForOption(filePath, path))
                            when (entry) {
                                is OptionDiff.New     -> green  { text("$cursor+ $path$arrow"); blue { textLine(tag) } }
                                is OptionDiff.Deleted -> red    { text("$cursor- $path$arrow"); blue { textLine(tag) } }
                                is OptionDiff.Changed -> yellow { text("$cursor~ $path$arrow"); blue { textLine(tag) } }
                                null                  -> textLine("$cursor? $path$arrow")
                            }
                        }
                    }

                    textLine()
                    green { text("+ nouveau") }; text("   "); red { text("- supprimé") }; text("   "); yellow { textLine("~ modifié") }
                    val escHint = if (pathStack.size > 1) "esc remonter" else "esc retour"
                    textLine("↑↓ naviguer   ↵ entrer   i diff IDE   $escHint   q quitter")
                }

                BrowseMode.VALUE -> {
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
                            green { textLine("+ Nouveau") }
                            textLine()
                            textLine("  Valeur : ${preview(optDiff.newValue)}")
                        }
                        is OptionDiff.Deleted -> {
                            red { textLine("- Supprimé") }
                            textLine()
                            textLine("  Ancienne valeur : ${preview(optDiff.oldValue)}")
                        }
                        is OptionDiff.Changed -> {
                            red   { textLine("  Avant : ${preview(optDiff.oldValue)}") }
                            green { textLine("  Après : ${preview(optDiff.newValue)}") }
                        }
                        null -> textLine("  (valeur inconnue)")
                    }

                    textLine()
                    val ideHint = if (optDiff is OptionDiff.Changed) "i diff IDE   " else ""
                    if (inDraft != null)
                        textLine("${ideHint}r retirer   esc retour   q quitter")
                    else
                        textLine("${ideHint}d défaut   o override   esc retour   q quitter")
                }

                BrowseMode.FINALIZE -> {
                    textLine("Finaliser le patch (${draftEntries.size} entrée(s))")
                    textLine()
                    text("Nom : "); input()
                    textLine()
                    textLine("↵ confirmer   esc annuler")
                }
            }
        }.runUntilKeyPressed(Keys.Q) {
            onInputChanged { patchName = input }
            onInputEntered {
                if (mode == BrowseMode.FINALIZE && patchName.isNotBlank()) {
                    DraftPatch.finalize(patchName)
                    draftEntries = DraftPatch.entries.toList()
                    patchName = ""
                    mode = BrowseMode.FILES
                }
            }
            onKeyPressed {
                when (mode) {
                    BrowseMode.FILES -> when (key) {
                        Keys.UP      -> if (fileIndex > 0) fileIndex--
                        Keys.DOWN    -> if (fileIndex < entries.size - 1) fileIndex++
                        CharKey('i') -> openFileIdeDiff(entries[fileIndex].key, entries[fileIndex].value.kind)
                        CharKey('f') -> if (draftEntries.isNotEmpty()) mode = BrowseMode.FINALIZE
                        Keys.ENTER   -> {
                            val fileDiff = entries[fileIndex].value
                            val allPaths = fileDiff.flatContentDiff.keys.filter { it != "$" }.toList()
                            if (directChildren(allPaths, "$").isNotEmpty()) {
                                pathStack = listOf("$")
                                diffIndex = 0
                                mode = BrowseMode.DIFF
                            } else {
                                valuePath = "$"
                                previousMode = BrowseMode.FILES
                                mode = BrowseMode.VALUE
                            }
                        }
                    }

                    BrowseMode.DIFF -> {
                        val allPaths = entries[fileIndex].value.flatContentDiff.keys.filter { it != "$" }.toList()
                        val visible = directChildren(allPaths, pathStack.last())
                        when (key) {
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
                                        previousMode = BrowseMode.DIFF
                                        mode = BrowseMode.VALUE
                                    }
                                }
                            }
                            Keys.ESC -> {
                                if (pathStack.size > 1) {
                                    pathStack = pathStack.dropLast(1)
                                    diffIndex = 0
                                } else {
                                    mode = BrowseMode.FILES
                                }
                            }
                        }
                    }

                    BrowseMode.VALUE -> {
                        val (filePath, fileDiff) = entries[fileIndex]
                        val optDiff = valuePath?.let { fileDiff.flatContentDiff[it] }
                        val vp = valuePath ?: return@onKeyPressed
                        val inDraft = draftForOption(filePath, vp)

                        when (key) {
                            Keys.ESC     -> mode = previousMode
                            CharKey('i') -> if (optDiff is OptionDiff.Changed) {
                                val ext = filePath.toString().substringAfterLast('.', "txt")
                                openIdeDiff(optDiff.oldValue, optDiff.newValue, ext)
                            }
                            CharKey('r') -> if (inDraft != null) {
                                DraftPatch.removeEntry(filePath.toString(), vp)
                                draftEntries = DraftPatch.entries.toList()
                            }
                            CharKey('d') -> if (inDraft == null) when (optDiff) {
                                is OptionDiff.New     -> { DraftPatch.setValueEntry(optDiff, PatchMode.DEFAULT);    draftEntries = DraftPatch.entries.toList() }
                                is OptionDiff.Changed -> { DraftPatch.setValueEntry(optDiff, PatchMode.DEFAULT);    draftEntries = DraftPatch.entries.toList() }
                                is OptionDiff.Deleted -> { DraftPatch.setDeletionEntry(optDiff, PatchMode.DEFAULT); draftEntries = DraftPatch.entries.toList() }
                                null -> Unit
                            }
                            CharKey('o') -> if (inDraft == null) when (optDiff) {
                                is OptionDiff.New     -> { DraftPatch.setValueEntry(optDiff, PatchMode.OVERRIDE);    draftEntries = DraftPatch.entries.toList() }
                                is OptionDiff.Changed -> { DraftPatch.setValueEntry(optDiff, PatchMode.OVERRIDE);    draftEntries = DraftPatch.entries.toList() }
                                is OptionDiff.Deleted -> { DraftPatch.setDeletionEntry(optDiff, PatchMode.OVERRIDE); draftEntries = DraftPatch.entries.toList() }
                                null -> Unit
                            }
                        }
                    }

                    BrowseMode.FINALIZE -> when (key) {
                        Keys.ESC -> {
                            patchName = ""
                            mode = BrowseMode.FILES
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
