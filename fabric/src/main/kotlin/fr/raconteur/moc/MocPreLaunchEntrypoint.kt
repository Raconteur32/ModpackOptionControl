package fr.raconteur.moc

import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.platform.FabricPlatformService
import fr.raconteur.moc.platform.PlatformService
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import org.slf4j.LoggerFactory

object MocPreLaunchEntrypoint : PreLaunchEntrypoint {
    private val logger = LoggerFactory.getLogger("moc")

    override fun onPreLaunch() {
        PlatformService.INSTANCE = FabricPlatformService
        MocMigration.migrate()

        McInstanceMocFileSystem.applyPending(
            onApplied = { patchName -> logger.info("[moc] Applied patch: $patchName") },
            onError   = { patchName, e -> logger.error("[moc] Failed to apply patch '$patchName': ${e.message}") }
        )

    }
}
