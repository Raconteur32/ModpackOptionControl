package fr.raconteur.moc.web

import fr.raconteur.moc.filesystem.MocFileSystem
import java.nio.file.Path

// Dedicated, properly-constructed empty filesystem used to compute the "showAll=true"
// diff (every current option appears as NEW when diffed against nothing). Points at a
// hardcoded, PlatformService-independent path that is guaranteed not to exist — safe
// because MocFileSystem.scan() is a no-op when rootPath is not a directory.
object EmptyMocFileSystem : MocFileSystem(
    rootPath = Path.of(System.getProperty("java.io.tmpdir")).resolve("moc-web-empty-fs-do-not-create")
)
