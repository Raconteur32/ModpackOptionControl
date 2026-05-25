package fr.raconteur.moc.cli

import com.varabyte.kotter.foundation.input.Keys
import com.varabyte.kotter.foundation.input.onKeyPressed
import com.varabyte.kotter.foundation.input.runUntilKeyPressed
import com.varabyte.kotter.foundation.liveVarOf
import com.varabyte.kotter.foundation.session
import com.varabyte.kotter.foundation.text.bold
import com.varabyte.kotter.foundation.text.green
import com.varabyte.kotter.foundation.text.text
import com.varabyte.kotter.foundation.text.textLine
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.platform.PlatformService

fun runFileBrowser() {
    val files = McInstanceMocFileSystem.files

    session {
        var selectedIndex by liveVarOf(0)

        section {
            textLine("Instance : ${PlatformService.INSTANCE.getGameDir()}")
            textLine()
            files.forEachIndexed { i, file ->
                if (i == selectedIndex) {
                    green { text("> ") }
                    bold { textLine(file.relativePath.toString()) }
                } else {
                    textLine("  ${file.relativePath}")
                }
            }
            textLine()
            textLine("↑↓ naviguer   q quitter")
        }.runUntilKeyPressed(Keys.Q) {
            onKeyPressed {
                when (key) {
                    Keys.UP -> if (selectedIndex > 0) selectedIndex--
                    Keys.DOWN -> if (selectedIndex < files.size - 1) selectedIndex++
                }
            }
        }
    }
}
