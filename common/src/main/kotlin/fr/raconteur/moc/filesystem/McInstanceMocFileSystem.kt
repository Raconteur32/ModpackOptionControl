package fr.raconteur.moc.filesystem

import fr.raconteur.moc.MocSettings
import fr.raconteur.moc.platform.PlatformService

object McInstanceMocFileSystem : MocFileSystem(
    rootPath      = PlatformService.INSTANCE.getGameDir(),
    ignoredPaths  = MocSettings.ignoredPaths,
    hasRef        = true,
    onRefError    = { patchName, e -> PlatformService.INSTANCE.logError("[moc] Failed to rebuild ref for patch '$patchName': ${e.message}", e) }
)
