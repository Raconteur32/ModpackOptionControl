package fr.raconteur.moc.platform

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Path

object FabricPlatformService : PlatformService {
    private val logger = LoggerFactory.getLogger("moc")

    override fun getPlatformName(): String = "Fabric"
    override fun getGameDir(): Path = FabricLoader.getInstance().gameDir
    override fun getConfigDir(): Path = FabricLoader.getInstance().configDir

    override fun logInfo(message: String) = logger.info(message)
    override fun logError(message: String, e: Exception?) {
        if (e != null) logger.error(message, e) else logger.error(message)
    }
    override fun logCritical(message: String, e: Exception?) {
        if (e != null) logger.error("[CRITICAL] $message", e) else logger.error("[CRITICAL] $message")
    }
}
