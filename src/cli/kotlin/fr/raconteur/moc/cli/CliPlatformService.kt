package fr.raconteur.moc.cli

import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

object CliPlatformService : PlatformService {

    private val detectedGameDir: Path by lazy {
        val candidates = listOf(
            Path.of("run"),
            Path.of(".")
        )
        candidates.firstOrNull { isValidGameDir(it) }
            ?: error("No valid Minecraft instance found. Expected a directory containing config/, mods/ and options.txt.")
    }

    override fun getPlatformName(): String = "Cli"

    override fun getGameDir(): Path = detectedGameDir

    override fun getConfigDir(): Path = detectedGameDir.resolve("config")

    private fun isValidGameDir(path: Path): Boolean =
        path.isDirectory()
            && path.resolve("config").isDirectory()
            && path.resolve("mods").isDirectory()
            && path.resolve("options.txt").exists()
}
