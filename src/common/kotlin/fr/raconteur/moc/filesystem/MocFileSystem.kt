package fr.raconteur.moc.filesystem

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import com.jayway.jsonpath.spi.json.GsonJsonProvider
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider
import fr.raconteur.moc.versioning.EntryKind
import fr.raconteur.moc.versioning.Patch
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchMode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

open class MocFileSystem(
    private val rootPath: Path,
    private val ignoredPaths: List<Path> = emptyList()
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val metadataJsonFile: Path = rootPath.resolve(".mocmetadata.json")
    private val allMetadata: MutableMap<String, MutableMap<String, String>> = loadAllMetadata()
    private var metadataDirty = false

    private var _files: List<MocFile> = scanFiles()
    val files: List<MocFile> get() = _files

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

    fun reload() {
        metadataDirty = false
        _files = scanFiles()
        if (metadataDirty) saveAllMetadata()
    }

    fun applyPatch(patch: Patch, forceDelete: Boolean) {
        for (entry in patch.entries) {
            if (shouldApply(entry, forceDelete)) applyEntry(entry)
        }
    }

    private fun shouldApply(entry: PatchEntry, forceDelete: Boolean): Boolean =
        when (entry.mode) {
            PatchMode.OVERRIDE -> true
            PatchMode.DEFAULT  -> when (entry.kind) {
                EntryKind.VALUE    -> !entryExists(entry)
                EntryKind.DELETION -> forceDelete
            }
        }

    private fun entryExists(entry: PatchEntry): Boolean {
        val path = Path.of(entry.filePath)
        if (entry.optionPath == "$") return hasFile(path)
        val file = files.find { it.relativePath == path } ?: return false
        return file.getFlatContent().containsKey(entry.optionPath)
    }

    private fun applyEntry(entry: PatchEntry) {
        val absPath = rootPath.resolve(entry.filePath).toFile()
        if (entry.optionPath == "$") {
            when (entry.kind) {
                EntryKind.VALUE -> {
                    absPath.parentFile.mkdirs()
                    absPath.writeText(gson.toJson(entry.toValue))
                }
                EntryKind.DELETION -> absPath.delete()
            }
        } else {
            when (entry.kind) {
                EntryKind.VALUE -> {
                    val content = if (absPath.exists()) absPath.readText() else "{}"
                    absPath.parentFile.mkdirs()
                    absPath.writeText(setJsonValue(content, entry.optionPath, entry.toValue))
                }
                EntryKind.DELETION -> {
                    if (absPath.exists()) {
                        absPath.writeText(removeJsonKey(absPath.readText(), entry.optionPath))
                    }
                }
            }
        }
    }

    private fun setJsonValue(content: String, optionPath: String, value: Any?): String {
        val config = Configuration.builder()
            .jsonProvider(GsonJsonProvider())
            .mappingProvider(GsonMappingProvider())
            .build()
        val document = JsonPath.using(config).parse(content)
        val jsonValue = gson.toJsonTree(value)
        try {
            document.set(optionPath, jsonValue)
        } catch (_: PathNotFoundException) {
            // Path doesn't exist yet — try to create via parent
            val lastDot = optionPath.lastIndexOf('.')
            if (lastDot > 1) {
                val parentPath = optionPath.substring(0, lastDot)
                val key = optionPath.substring(lastDot + 1)
                try { document.put(parentPath, key, jsonValue) } catch (_: Exception) {}
            }
        }
        return gson.toJson(document.read<JsonElement>("$"))
    }

    private fun removeJsonKey(content: String, optionPath: String): String {
        val config = Configuration.builder()
            .jsonProvider(GsonJsonProvider())
            .mappingProvider(GsonMappingProvider())
            .build()
        val document = JsonPath.using(config).parse(content)
        try { document.delete(optionPath) } catch (_: Exception) {}
        return gson.toJson(document.read<JsonElement>("$"))
    }

    private fun scanFiles(): List<MocFile> = if (!rootPath.isDirectory()) emptyList() else
        Files.walk(rootPath)
            .filter { file ->
                file.isRegularFile()
                    && file != metadataJsonFile
                    && ignoredPaths.none { file.startsWith(rootPath.resolve(it)) }
                    && !MocFile.isBinary(file)
            }
            .map { MocFile(this, rootPath.relativize(it)) }
            .toList()

    private fun loadAllMetadata(): MutableMap<String, MutableMap<String, String>> {
        if (!metadataJsonFile.toFile().exists()) return mutableMapOf()
        return try {
            val json = metadataJsonFile.toFile().readText()
            val type = object : TypeToken<MutableMap<String, MutableMap<String, String>>>() {}.type
            com.google.gson.Gson().fromJson(json, type) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveAllMetadata() {
        metadataJsonFile.toFile().writeText(gson.toJson(allMetadata))
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
