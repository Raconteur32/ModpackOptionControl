package fr.raconteur.moc

import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Path

object MocMigration {
    fun migrate() {
        migrateFileSystem(PlatformService.INSTANCE.getGameDir())
        migrateFileSystem(PlatformService.INSTANCE.getConfigDir().resolve("moc/dev-ref"))

        val patchsDir = PlatformService.INSTANCE.getConfigDir().resolve("moc/patchs")
        patchsDir.toFile().listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { renameLegacy(it.toPath().resolve(".mocmeta.json"), "mocmeta.json") }
    }

    private fun migrateFileSystem(rootPath: Path) {
        renameLegacy(rootPath.resolve(".mocmetadata.json"), "mocmetadata.json")
        renameLegacy(rootPath.resolve(".mocappliedpatches.json"), "mocappliedpatches.json")
    }

    private fun renameLegacy(legacy: Path, newName: String) {
        val src = legacy.toFile()
        if (!src.exists()) return
        val dst = legacy.resolveSibling(newName).toFile()
        if (!dst.exists()) src.renameTo(dst)
    }
}
