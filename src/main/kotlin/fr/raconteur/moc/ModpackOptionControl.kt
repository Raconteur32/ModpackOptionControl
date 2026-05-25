package fr.raconteur.moc

import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Path

object ModpackOptionControl : ModInitializer {
    private val logger = LoggerFactory.getLogger("moc")

    fun getInstanceDir(): Path = FabricLoader.getInstance().gameDir

	override fun onInitialize() {
		logger.info("Hello Fabric world!")
	}
}