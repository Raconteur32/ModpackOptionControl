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

fun runDiffBrowser() {
    ScriptUtils.ensureScripts()
    val diff = McInstanceMocFileSystem.diffFrom(McInstanceRefMocFileSystem)
    val entries = diff.entries.sortedBy { it.key.toString() }

    session {
        var selectedIndex by liveVarOf(0)

        section {
            textLine("Diff — ${entries.size} fichier(s) modifié(s)")
            textLine()

            entries.forEachIndexed { i, (path, fileDiff) ->
                val cursor = if (i == selectedIndex) "> " else "  "

                when (fileDiff.diffType) {
                    DiffType.NEW     -> green  { text(cursor); bold { textLine("+ $path") } }
                    DiffType.DELETED -> red    { text(cursor); bold { textLine("- $path") } }
                    DiffType.CHANGED -> yellow { text(cursor); bold { textLine("~ $path") } }
                }
            }

            textLine()

            if (entries.isNotEmpty()) {
                val selected = entries[selectedIndex]
                val contentDiff = selected.value.flatContentDiff

                if (contentDiff != null && contentDiff.isNotEmpty()) {
                    textLine("--- ${selected.key} ---")
                    contentDiff.entries.sortedBy { it.key }.forEach { (jsonPath, type) ->
                        when (type) {
                            DiffType.NEW     -> green  { textLine("  + $jsonPath") }
                            DiffType.DELETED -> red    { textLine("  - $jsonPath") }
                            DiffType.CHANGED -> yellow { textLine("  ~ $jsonPath") }
                        }
                    }
                }
            }

            textLine()
            textLine("↑↓ naviguer   q quitter")
        }.runUntilKeyPressed(Keys.Q) {
            onKeyPressed {
                when (key) {
                    Keys.UP   -> if (selectedIndex > 0) selectedIndex--
                    Keys.DOWN -> if (selectedIndex < entries.size - 1) selectedIndex++
                }
            }
        }
    }
}
