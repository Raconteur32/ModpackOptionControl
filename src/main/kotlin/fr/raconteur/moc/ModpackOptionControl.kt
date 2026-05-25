package fr.raconteur.moc

import fr.raconteur.moc.platform.FabricPlatformService
import fr.raconteur.moc.platform.PlatformService
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object ModpackOptionControl : ModInitializer {
    private val logger = LoggerFactory.getLogger("moc")

    override fun onInitialize() {
        PlatformService.INSTANCE = FabricPlatformService
        logger.info("Hello Fabric world!")
    }
}