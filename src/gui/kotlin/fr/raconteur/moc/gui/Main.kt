package fr.raconteur.moc.gui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import fr.raconteur.moc.filesystem.McInstanceRefMocFileSystem
import fr.raconteur.moc.platform.PlatformService

fun main() {
    PlatformService.INSTANCE = GuiPlatformService
    McInstanceRefMocFileSystem.regenerateRefFiles()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "MOC — Modpack Options Control",
            state = rememberWindowState(width = 1100.dp, height = 720.dp)
        ) {
            App()
        }
    }
}
