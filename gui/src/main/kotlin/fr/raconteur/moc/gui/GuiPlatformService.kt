package fr.raconteur.moc.gui

import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

object GuiPlatformService : PlatformService {

    private val detectedGameDir: Path by lazy {
        val candidates = listOf(Path.of("run"), Path.of("."))
        candidates.firstOrNull { isValidGameDir(it) }
            ?: error("No valid Minecraft instance found. Expected config/, mods/ and options.txt.")
    }

    override fun getPlatformName(): String = "Gui"

    override fun getGameDir(): Path = detectedGameDir

    override fun getConfigDir(): Path = detectedGameDir.resolve("config")

    private fun isValidGameDir(path: Path): Boolean =
        path.isDirectory()
            && path.resolve("config").isDirectory()
            && path.resolve("mods").isDirectory()
            && path.resolve("options.txt").exists()
}
