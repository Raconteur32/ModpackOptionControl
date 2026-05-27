package fr.raconteur.moc.filesystem

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

open class MocFileSystem(
    private val rootPath: Path,
    private val ignoredPaths: List<Path> = emptyList()
) {
    private val metadataJsonFile: Path = rootPath.resolve(".mocmetadata.json")
    private val allMetadata: MutableMap<String, MutableMap<String, String>> = loadAllMetadata()
    private var metadataDirty = false

    val files: List<MocFile> = if (!rootPath.isDirectory()) emptyList() else
        Files.walk(rootPath)
            .filter { file ->
                file.isRegularFile()
                    && file != metadataJsonFile
                    && ignoredPaths.none { file.startsWith(rootPath.resolve(it)) }
                    && !MocFile.isBinary(file)
            }
            .map { MocFile(this, rootPath.relativize(it)) }
            .toList()

    init {
        if (metadataDirty) saveAllMetadata()
    }

    fun getRootPath(): Path = rootPath
    fun getMetadataFile(): Path = metadataJsonFile
    fun hasFile(relativePath: Path): Boolean = files.any { it.relativePath == relativePath }

    internal fun getFileMetadata(relativePath: Path): Map<String, String>? =
        allMetadata[relativePath.toString()]

    internal fun setFileMetadata(relativePath: Path, metadata: Map<String, String>) {
        allMetadata[relativePath.toString()] = metadata.toMutableMap()
        metadataDirty = true
    }

    private fun loadAllMetadata(): MutableMap<String, MutableMap<String, String>> {
        if (!metadataJsonFile.toFile().exists()) return mutableMapOf()
        return try {
            val json = metadataJsonFile.toFile().readText()
            val type = object : TypeToken<MutableMap<String, MutableMap<String, String>>>() {}.type
            Gson().fromJson(json, type) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveAllMetadata() {
        metadataJsonFile.toFile().writeText(
            GsonBuilder().setPrettyPrinting().create().toJson(allMetadata)
        )
    }

    fun diffFrom(other: MocFileSystem): FileSystemDiff {
        val result = FileSystemDiff()
        val thisFiles = files.associateBy { it.relativePath }
        val otherFiles = other.files.associateBy { it.relativePath }

        for (path in otherFiles.keys - thisFiles.keys) {
            val ghostCurrent = MocFile(this, path)
            result.addDeleted(path, ghostCurrent.diffFrom(otherFiles[path]!!))
        }
        for (path in thisFiles.keys - otherFiles.keys) {
            val ghostRef = MocFile(other, path)
            result.addNew(path, thisFiles[path]!!.diffFrom(ghostRef))
        }
        for (path in thisFiles.keys intersect otherFiles.keys) {
            val contentDiff = thisFiles[path]!!.diffFrom(otherFiles[path]!!)
            if (contentDiff.isNotEmpty()) result.addChanged(path, contentDiff)
        }

        return result
    }
}
