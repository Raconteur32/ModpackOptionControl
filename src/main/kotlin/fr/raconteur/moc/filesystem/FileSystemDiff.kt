package fr.raconteur.moc.filesystem

import fr.raconteur.moc.content.DiffType
import fr.raconteur.moc.content.FlatContentDiff
import java.nio.file.Path

class FileSystemDiff(
    private val map: MutableMap<Path, MocFileDiff> = mutableMapOf()
) : Map<Path, MocFileDiff> by map {

    fun addNew(path: Path) { map[path] = MocFileDiff.new() }
    fun addDeleted(path: Path) { map[path] = MocFileDiff.deleted() }
    fun addChanged(path: Path, diff: FlatContentDiff) { map[path] = MocFileDiff.changed(diff) }

    fun getNewPaths(): Set<Path> = map.filterValues { it.diffType == DiffType.NEW }.keys
    fun getDeletedPaths(): Set<Path> = map.filterValues { it.diffType == DiffType.DELETED }.keys
    fun getChangedPaths(): Set<Path> = map.filterValues { it.diffType == DiffType.CHANGED }.keys
}
