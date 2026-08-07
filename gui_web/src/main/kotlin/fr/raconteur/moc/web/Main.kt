package fr.raconteur.moc.web

import fr.raconteur.moc.MocMigration
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.platform.PlatformService
import fr.raconteur.moc.versioning.PatchList
import fr.raconteur.moc.web.McInstanceRefMocFileSystem
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import java.awt.Desktop
import java.net.URI

fun main() {
    PlatformService.INSTANCE = WebPlatformService

    MocMigration.migrate()
    PatchList.runStartupCleanup()
    McInstanceMocFileSystem.applyPending(
        onError = { patchName, e -> PlatformService.INSTANCE.logError("[moc] Failed to apply pending patch '$patchName': ${e.message}", e) }
    )
    McInstanceRefMocFileSystem.regenerateIfStale()

    val port      = System.getenv("MOC_PORT")?.toIntOrNull() ?: 7421
    val noBrowser = System.getenv("MOC_NO_BROWSER") == "true"
    embeddedServer(CIO, port = port, module = { mocModule() }).start(wait = false)
    if (!noBrowser && Desktop.isDesktopSupported())
        Desktop.getDesktop().browse(URI("http://localhost:$port"))
    Thread.currentThread().join()
}
