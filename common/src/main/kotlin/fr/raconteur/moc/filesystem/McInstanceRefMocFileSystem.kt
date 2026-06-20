package fr.raconteur.moc.filesystem

import fr.raconteur.moc.MocSettings
import fr.raconteur.moc.platform.PlatformService
import fr.raconteur.moc.versioning.PatchList

object McInstanceRefMocFileSystem : MocFileSystem(
    rootPath = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/ref"),
    ignoredPaths = MocSettings.ignoredPaths
) {
    fun regenerateRefFiles() {
        getRootPath().toFile().walkTopDown()
            .sortedDescending()
            .filter { it != getRootPath().toFile() }
            .forEach { it.delete() }
        reload()

        applyMultiplePatches(
            PatchList.getAll(),
            onError = { patchName, e -> PlatformService.INSTANCE.logError("[moc] Failed to regenerate ref for patch '$patchName': ${e.message}", e) }
        )
    }
}