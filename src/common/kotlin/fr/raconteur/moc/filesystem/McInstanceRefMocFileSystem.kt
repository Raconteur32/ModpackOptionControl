package fr.raconteur.moc.filesystem

import fr.raconteur.moc.MocSettings
import fr.raconteur.moc.platform.PlatformService
import fr.raconteur.moc.versioning.Patch
import fr.raconteur.moc.versioning.PatchList

object McInstanceRefMocFileSystem : MocFileSystem(
    rootPath = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev-ref"),
    ignoredPaths = MocSettings.ignoredPaths
) {
    fun regenerateRefFiles() {
        getRootPath().toFile().walkTopDown()
            .sortedDescending()
            .filter { it != getRootPath().toFile() }
            .forEach { it.delete() }
        reload()

        for (patchName in PatchList.getAll()) {
            applyPatch(Patch.load(patchName), forceDelete = true)
        }
    }
}
