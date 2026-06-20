package fr.raconteur.moc

import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Path

object MocMigration {
    fun migrate() {
        migrateFileSystem(PlatformService.INSTANCE.getGameDir())
        migrateFileSystem(PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/ref"))

        val patchsDir = PlatformService.INSTANCE.getConfigDir().resolve("moc/patchs")
        patchsDir.toFile().listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { renameLegacy(it.toPath().resolve(".mocmeta.json"), it.toPath().resolve("mocmeta.json")) }
    }

    private fun migrateFileSystem(rootPath: Path) {
        val metasDir = rootPath.resolve("mocfsmetas")
        // Very old dot-file names → mocfsmetas/
        renameLegacy(rootPath.resolve(".mocmetadata.json"),      metasDir.resolve("mocmetadata.json"))
        renameLegacy(rootPath.resolve(".mocappliedpatches.json"), metasDir.resolve("mocappliedpatches.json"))
        // Flat root files → mocfsmetas/
        renameLegacy(rootPath.resolve("mocmetadata.json"),       metasDir.resolve("mocmetadata.json"))
        renameLegacy(rootPath.resolve("mocappliedpatches.json"), metasDir.resolve("mocappliedpatches.json"))
        // Logs directory → mocfsmetas/
        moveLegacyDir(rootPath.resolve("mocappliedlogs"),        metasDir.resolve("mocappliedlogs"))
    }

    private fun renameLegacy(src: Path, dst: Path) {
        val srcFile = src.toFile()
        if (!srcFile.exists()) return
        dst.toFile().parentFile?.mkdirs()
        if (!dst.toFile().exists()) srcFile.renameTo(dst.toFile())
    }

    private fun moveLegacyDir(src: Path, dst: Path) {
        val srcFile = src.toFile()
        if (!srcFile.exists() || !srcFile.isDirectory) return
        dst.toFile().parentFile?.mkdirs()
        if (!dst.toFile().exists()) srcFile.renameTo(dst.toFile())
    }
}
