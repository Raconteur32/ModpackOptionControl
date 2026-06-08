package fr.raconteur.moc.platform

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

object FabricPlatformService : PlatformService {
    override fun getPlatformName(): String = "Fabric"
    override fun getGameDir(): Path = FabricLoader.getInstance().gameDir
    override fun getConfigDir(): Path = FabricLoader.getInstance().configDir
}
