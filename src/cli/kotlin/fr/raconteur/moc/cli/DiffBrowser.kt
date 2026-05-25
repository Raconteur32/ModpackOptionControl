package fr.raconteur.moc.cli

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
import fr.raconteur.moc.content.DiffType
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.filesystem.McInstanceRefMocFileSystem
import fr.raconteur.moc.lua.ScriptUtils

private enum class BrowseMode { FILES, DIFF }

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
        var mode      by liveVarOf(BrowseMode.FILES)
        var fileIndex by liveVarOf(0)
        var diffIndex by liveVarOf(0)
        var pathStack by liveVarOf(listOf("$"))

        section {
            when (mode) {
                BrowseMode.FILES -> {
                    textLine("Diff — ${entries.size} fichier(s) modifié(s)")
                    textLine()
                    entries.forEachIndexed { i, (path, fileDiff) ->
                        val cursor = if (i == fileIndex) "> " else "  "
                        val arrow = if (fileDiff.diffType == DiffType.CHANGED && fileDiff.flatContentDiff?.isNotEmpty() == true) " ▶" else ""
                        when (fileDiff.diffType) {
                            DiffType.NEW     -> green  { text(cursor); bold { textLine("+ $path") } }
                            DiffType.DELETED -> red    { text(cursor); bold { textLine("- $path") } }
                            DiffType.CHANGED -> yellow { text(cursor); bold { textLine("~ $path$arrow") } }
                        }
                    }
                    textLine()
                    green { text("+ nouveau") }; text("   "); red { text("- supprimé") }; text("   "); yellow { textLine("~ modifié") }
                    textLine("↑↓ naviguer   ↵ ouvrir   q quitter")
                }

                BrowseMode.DIFF -> {
                    val (filePath, fileDiff) = entries[fileIndex]
                    val allPaths = fileDiff.flatContentDiff?.keys?.filter { it != "$" }?.toList() ?: emptyList()
                    val currentParent = pathStack.last()
                    val visible = directChildren(allPaths, currentParent)

                    bold { textLine("~ $filePath") }
                    textLine("  ${pathStack.joinToString(" › ")}")
                    textLine()

                    if (visible.isEmpty()) {
                        textLine("  (aucun sous-élément)")
                    } else {
                        visible.forEachIndexed { i, path ->
                            val cursor = if (i == diffIndex) "> " else "  "
                            val type = fileDiff.flatContentDiff?.get(path) ?: DiffType.CHANGED
                            val arrow = if (directChildren(allPaths, path).isNotEmpty()) " ▶" else ""
                            when (type) {
                                DiffType.NEW     -> green  { textLine("$cursor+ $path$arrow") }
                                DiffType.DELETED -> red    { textLine("$cursor- $path$arrow") }
                                DiffType.CHANGED -> yellow { textLine("$cursor~ $path$arrow") }
                            }
                        }
                    }

                    textLine()
                    green { text("+ nouveau") }; text("   "); red { text("- supprimé") }; text("   "); yellow { textLine("~ modifié") }
                    val escHint = if (pathStack.size > 1) "esc remonter" else "esc retour"
                    textLine("↑↓ naviguer   ↵ entrer   $escHint   q quitter")
                }
            }
        }.runUntilKeyPressed(Keys.Q) {
            onKeyPressed {
                when (mode) {
                    BrowseMode.FILES -> when (key) {
                        Keys.UP    -> if (fileIndex > 0) fileIndex--
                        Keys.DOWN  -> if (fileIndex < entries.size - 1) fileIndex++
                        Keys.ENTER -> {
                            val fileDiff = entries[fileIndex].value
                            if (fileDiff.diffType == DiffType.CHANGED && fileDiff.flatContentDiff?.isNotEmpty() == true) {
                                pathStack = listOf("$")
                                diffIndex = 0
                                mode = BrowseMode.DIFF
                            }
                        }
                    }
                    BrowseMode.DIFF -> {
                        val allPaths = entries[fileIndex].value.flatContentDiff?.keys?.toList() ?: emptyList()
                        val visible = directChildren(allPaths, pathStack.last())
                        when (key) {
                            Keys.UP    -> if (diffIndex > 0) diffIndex--
                            Keys.DOWN  -> if (diffIndex < visible.size - 1) diffIndex++
                            Keys.ENTER -> {
                                if (visible.isNotEmpty()) {
                                    val selected = visible[diffIndex]
                                    if (directChildren(allPaths, selected).isNotEmpty()) {
                                        pathStack = pathStack + selected
                                        diffIndex = 0
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
                }
            }
        }
    }
}
