package fr.raconteur.moc.filesystem

import fr.raconteur.moc.MocSettings
import fr.raconteur.moc.platform.PlatformService

object McInstanceMocFileSystem : MocFileSystem(
    rootPath = PlatformService.INSTANCE.getGameDir(),
    ignoredPaths = MocSettings.ignoredPaths
)
