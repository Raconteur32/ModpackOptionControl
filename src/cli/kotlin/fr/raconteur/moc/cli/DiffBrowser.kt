package fr.raconteur.moc.cli

import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Keys
import com.varabyte.kotter.foundation.input.onKeyPressed
import com.varabyte.kotter.foundation.input.runUntilKeyPressed
import com.varabyte.kotter.foundation.liveVarOf
import com.varabyte.kotter.foundation.session
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
import java.nio.file.Files

private enum class BrowseMode { FILES, DIFF, VALUE }

private fun openFileIdeDiff(filePath: java.nio.file.Path, kind: FileDiffKind) {
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

fun runDiffBrowser() {
    ScriptUtils.ensureScripts()
    val diff = McInstanceMocFileSystem.diffFrom(McInstanceRefMocFileSystem)
    val entries = diff.entries.sortedBy { it.key.toString() }

    session {
        var mode         by liveVarOf(BrowseMode.FILES)
        var previousMode by liveVarOf(BrowseMode.FILES)
        var fileIndex    by liveVarOf(0)
        var diffIndex    by liveVarOf(0)
        var pathStack    by liveVarOf(listOf("$"))
        var valuePath    by liveVarOf<String?>(null)

        section {
            when (mode) {
                BrowseMode.FILES -> {
                    textLine("Diff — ${entries.size} fichier(s) modifié(s)")
                    textLine()
                    entries.forEachIndexed { i, (path, fileDiff) ->
                        val cursor = if (i == fileIndex) "> " else "  "
                        val arrow = if (fileDiff.flatContentDiff.isNotEmpty()) " ▶" else ""
                        when (fileDiff.kind) {
                            FileDiffKind.NEW     -> green  { text(cursor); bold { textLine("+ $path$arrow") } }
                            FileDiffKind.DELETED -> red    { text(cursor); bold { textLine("- $path$arrow") } }
                            FileDiffKind.CHANGED -> yellow { text(cursor); bold { textLine("~ $path$arrow") } }
                        }
                    }
                    textLine()
                    green { text("+ nouveau") }; text("   "); red { text("- supprimé") }; text("   "); yellow { textLine("~ modifié") }
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
                            when (entry) {
                                is OptionDiff.New     -> green  { textLine("$cursor+ $path$arrow") }
                                is OptionDiff.Deleted -> red    { textLine("$cursor- $path$arrow") }
                                is OptionDiff.Changed -> yellow { textLine("$cursor~ $path$arrow") }
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
                    val (_, fileDiff) = entries[fileIndex]
                    val entry = valuePath?.let { fileDiff.flatContentDiff[it] }
                    fun preview(v: Any?) = v?.toString()?.replace("\n", "↵")?.take(120)?.let {
                        if ((v.toString().length ?: 0) > 120) "$it…" else it
                    } ?: "null"

                    bold { textLine(valuePath ?: "") }
                    textLine()

                    when (entry) {
                        is OptionDiff.New -> {
                            green { textLine("+ Nouveau") }
                            textLine()
                            textLine("  Valeur : ${preview(entry.newValue)}")
                        }
                        is OptionDiff.Deleted -> {
                            red { textLine("- Supprimé") }
                            textLine()
                            textLine("  Ancienne valeur : ${preview(entry.oldValue)}")
                        }
                        is OptionDiff.Changed -> {
                            red   { textLine("  Avant : ${preview(entry.oldValue)}") }
                            green { textLine("  Après : ${preview(entry.newValue)}") }
                        }
                        null -> textLine("  (valeur inconnue)")
                    }

                    textLine()
                    if (entry is OptionDiff.Changed) textLine("esc retour   i ouvrir dans l'IDE   q quitter")
                    else textLine("esc retour   q quitter")
                }
            }
        }.runUntilKeyPressed(Keys.Q) {
            onKeyPressed {
                when (mode) {
                    BrowseMode.FILES -> when (key) {
                        Keys.UP    -> if (fileIndex > 0) fileIndex--
                        Keys.DOWN  -> if (fileIndex < entries.size - 1) fileIndex++
                        CharKey('i') -> {
                            val (path, fileDiff) = entries[fileIndex]
                            openFileIdeDiff(path, fileDiff.kind)
                        }
                        Keys.ENTER -> {
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
                            CharKey('i') -> {
                                val (path, fileDiff) = entries[fileIndex]
                                openFileIdeDiff(path, fileDiff.kind)
                            }
                            Keys.UP    -> if (diffIndex > 0) diffIndex--
                            Keys.DOWN  -> if (diffIndex < visible.size - 1) diffIndex++
                            Keys.ENTER -> {
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

                    BrowseMode.VALUE -> when (key) {
                        Keys.ESC -> mode = previousMode
                        CharKey('i') -> {
                            val fileDiff = entries[fileIndex].value
                            val entry = valuePath?.let { fileDiff.flatContentDiff[it] }
                            if (entry is OptionDiff.Changed) {
                                val ext = entries[fileIndex].key.toString().substringAfterLast('.', "txt")
                                openIdeDiff(entry.oldValue, entry.newValue, ext)
                            }
                        }
                    }
                }
            }
        }
    }
}
