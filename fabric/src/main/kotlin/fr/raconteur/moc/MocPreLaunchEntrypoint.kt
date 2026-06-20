package fr.raconteur.moc

import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.platform.FabricPlatformService
import fr.raconteur.moc.platform.PlatformService
import fr.raconteur.moc.versioning.Patch
import fr.raconteur.moc.versioning.PatchList
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import org.slf4j.LoggerFactory

object MocPreLaunchEntrypoint : PreLaunchEntrypoint {
    private val logger = LoggerFactory.getLogger("moc")

    override fun onPreLaunch() {
        PlatformService.INSTANCE = FabricPlatformService
        MocMigration.migrate()

        val applied = McInstanceMocFileSystem.appliedPatches.toSet()
        val toApply = PatchList.getAll().filter { it !in applied }

        if (toApply.isEmpty()) return

        for (patchName in toApply) {
            try {
                McInstanceMocFileSystem.applyPatch(Patch.load(patchName), forceDelete = false)
                logger.info("[moc] Applied patch: $patchName")
            } catch (e: Exception) {
                logger.error("[moc] Failed to apply patch '$patchName': ${e.message}")
                break
            }
        }

    }
}
