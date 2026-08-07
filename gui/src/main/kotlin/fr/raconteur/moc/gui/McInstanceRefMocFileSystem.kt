package fr.raconteur.moc.gui

import fr.raconteur.moc.MocSettings
import fr.raconteur.moc.filesystem.MocFileSystem
import fr.raconteur.moc.platform.PlatformService
import fr.raconteur.moc.versioning.PatchList

// Gui-module copy of the dev reference filesystem (extracted from common; see
// openspec/changes/extract-authoring-from-common). Frozen behavior: regenerates
// unconditionally at startup. gui is deprecated in favor of gui_web, whose copy
// regenerates only when its fingerprint detects staleness.
object McInstanceRefMocFileSystem : MocFileSystem(
    rootPath     = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/ref"),
    ignoredPaths = MocSettings.ignoredPaths,
    hasRef       = true,
    onRefError   = { patchName, e -> PlatformService.INSTANCE.logError("[moc] Failed to rebuild ref for patch '$patchName': ${e.message}", e) }
) {
    fun regenerateRefFiles() {
        getRootPath().toFile().walkTopDown()
            .sortedDescending()
            .filter { it != getRootPath().toFile() }
            .forEach { it.delete() }
        reload()

        applyMultiplePatches(
            PatchList.getAll(),
            forceOverride = true,
            onError = { patchName, e -> PlatformService.INSTANCE.logError("[moc] Failed to regenerate ref for patch '$patchName': ${e.message}", e) }
        )
    }
}
