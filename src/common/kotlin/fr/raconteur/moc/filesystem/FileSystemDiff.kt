package fr.raconteur.moc.filesystem

import fr.raconteur.moc.content.FlatContentDiff
import java.nio.file.Path

class FileSystemDiff(
    private val map: MutableMap<Path, MocFileDiff> = mutableMapOf()
) : Map<Path, MocFileDiff> by map {

    fun addNew(path: Path, diff: FlatContentDiff)     { map[path] = MocFileDiff.new(diff) }
    fun addDeleted(path: Path, diff: FlatContentDiff) { map[path] = MocFileDiff.deleted(diff) }
    fun addChanged(path: Path, diff: FlatContentDiff) { map[path] = MocFileDiff.changed(diff) }

    fun getNewPaths()     : Set<Path> = map.filterValues { it.kind == FileDiffKind.NEW }.keys
    fun getDeletedPaths() : Set<Path> = map.filterValues { it.kind == FileDiffKind.DELETED }.keys
    fun getChangedPaths() : Set<Path> = map.filterValues { it.kind == FileDiffKind.CHANGED }.keys
}
