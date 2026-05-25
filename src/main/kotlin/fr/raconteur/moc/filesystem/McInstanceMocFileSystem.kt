package fr.raconteur.moc.filesystem

import fr.raconteur.moc.ModpackOptionControl
import fr.raconteur.moc.MocSettings

object McInstanceMocFileSystem : MocFileSystem(
    rootPath = ModpackOptionControl.getInstanceDir(),
    metadataPath = ModpackOptionControl.getInstanceDir().resolve(".mocmetadata"),
    ignoredPaths = MocSettings.ignoredPaths
)