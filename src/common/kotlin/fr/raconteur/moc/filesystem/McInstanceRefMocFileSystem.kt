package fr.raconteur.moc.filesystem

import fr.raconteur.moc.MocSettings
import fr.raconteur.moc.platform.PlatformService
import fr.raconteur.moc.versioning.Patch
import fr.raconteur.moc.versioning.PatchList

object McInstanceRefMocFileSystem : MocFileSystem(
    rootPath = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev-ref"),
    ignoredPaths = MocSettings.ignoredPaths
) {
    fun generate() {
        getRootPath().toFile().walkTopDown()
            .sortedDescending()
            .filter { it != getRootPath().toFile() }
            .forEach { it.delete() }

        for (patchName in PatchList.getAll()) {
            val patch = Patch.load(patchName)
            reload()
            applyPatch(patch, forceDelete = true)
        }
        reload()
    }
}
