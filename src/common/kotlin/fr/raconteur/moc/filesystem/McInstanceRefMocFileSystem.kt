package fr.raconteur.moc.filesystem

import fr.raconteur.moc.MocSettings
import fr.raconteur.moc.platform.PlatformService

object McInstanceRefMocFileSystem : MocFileSystem(
    rootPath = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev-ref"),
    metadataPath = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev-ref/.mocmetadata"),
    ignoredPaths = MocSettings.ignoredPaths
)
