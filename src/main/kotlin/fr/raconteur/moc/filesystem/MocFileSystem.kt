package fr.raconteur.moc.filesystem

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

open class MocFileSystem(
    private val rootPath: Path,
    private val metadataPath: Path,
    private val ignoredPaths: List<Path> = emptyList()
) {
    val files: List<MocFile> = Files.walk(rootPath)
        .filter { file ->
            file.isRegularFile()
                && !file.startsWith(metadataPath)
                && ignoredPaths.none { file.startsWith(rootPath.resolve(it)) }
        }
        .map { MocFile(this, rootPath.relativize(it)) }
        .toList()

    fun getRootPath(): Path = rootPath
    fun getMetadataPath(): Path = metadataPath
    fun getFiles(): List<MocFile> = files
    fun hasFile(relativePath: Path): Boolean = files.any { it.relativePath == relativePath }

    fun diffFrom(other: MocFileSystem): FileSystemDiff {
        val result = FileSystemDiff()
        val thisFiles = files.associateBy { it.relativePath }
        val otherFiles = other.files.associateBy { it.relativePath }

        for (path in otherFiles.keys - thisFiles.keys) result.addDeleted(path)
        for (path in thisFiles.keys - otherFiles.keys) result.addNew(path)
        for (path in thisFiles.keys intersect otherFiles.keys) {
            val contentDiff = thisFiles[path]!!.diffFrom(otherFiles[path]!!)
            if (contentDiff.isNotEmpty()) result.addChanged(path, contentDiff)
        }

        return result
    }
}