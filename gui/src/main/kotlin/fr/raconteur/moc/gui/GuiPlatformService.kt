package fr.raconteur.moc.gui

import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

object GuiPlatformService : PlatformService {

    var gameDirOverride: Path? = null

    private val detectedGameDir: Path by lazy {
        val resolvedOverride = gameDirOverride
            ?: System.getProperty("moc.gameDir")?.takeIf { it.isNotEmpty() }?.let { Path.of(it) }
        resolvedOverride?.toAbsolutePath()?.normalize()?.also { override ->
            if (!isValidGameDir(override)) {
                System.err.println("[MOC] Specified game directory is not valid (missing config/ or mods/): $override")
                exitProcess(1)
            }
        } ?: run {
            val candidates = listOf(Path.of("."), Path.of(".."), Path.of("run"), Path.of("../fabric/run"), Path.of("../run"))
            candidates.firstOrNull { isValidGameDir(it) } ?: run {
                System.err.println("[MOC] No valid Minecraft instance found. Expected config/ and mods/ in one of: ${candidates.joinToString()}")
                exitProcess(1)
            }
        }
    }

    override fun getPlatformName(): String = "Gui"

    override fun logInfo(message: String) = println("[MOC] $message")
    override fun logError(message: String, e: Exception?) {
        System.err.println("[MOC][ERROR] $message")
        e?.printStackTrace(System.err)
    }
    override fun logCritical(message: String, e: Exception?) {
        System.err.println("[MOC][CRITICAL] $message")
        e?.printStackTrace(System.err)
    }

    override fun getGameDir(): Path = detectedGameDir

    override fun getConfigDir(): Path = detectedGameDir.resolve("config")

    private fun isValidGameDir(path: Path): Boolean =
        path.isDirectory()
            && path.resolve("config").isDirectory()
            && path.resolve("mods").isDirectory()
}
