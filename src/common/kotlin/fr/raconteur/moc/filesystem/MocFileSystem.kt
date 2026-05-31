package fr.raconteur.moc.filesystem

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import de.marhali.json5.Json5
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import com.jayway.jsonpath.spi.json.GsonJsonProvider
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider
import fr.raconteur.moc.content.TextContentType
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
    private val json5 = Json5()
    private val metadataJsonFile: Path     = rootPath.resolve(".mocmetadata.json")
    private val appliedPatchesFile: Path   = rootPath.resolve(".mocappliedpatches.json")
    private val allMetadata: MutableMap<String, MutableMap<String, String>> = loadAllMetadata()

    private val _files: MutableMap<Path, MocFile> = mutableMapOf()
    val files: Collection<MocFile> get() = _files.values

    private val _appliedPatches: MutableList<String> = loadAppliedPatches()
    val appliedPatches: List<String> get() = _appliedPatches.toList()

    init { scan() }

    fun getRootPath(): Path = rootPath
    fun getMetadataFile(): Path = metadataJsonFile
    fun hasFile(relativePath: Path): Boolean = _files.containsKey(relativePath)

    internal fun getFileMetadata(relativePath: Path): Map<String, String>? =
        allMetadata[relativePath.toString()]

    internal fun register(file: MocFile) {
        _files[file.relativePath] = file
        registerMetadata(file)
    }

    internal fun registerMetadata(file: MocFile) {
        val key = file.relativePath.toString()
        if (allMetadata[key] != file.metadata) {
            allMetadata[key] = file.metadata.toMutableMap()
            saveAllMetadata()
        }
    }

    fun removeFile(file: MocFile) {
        file.getAbsolutePath().toFile().delete()
        allMetadata.remove(file.relativePath.toString())
        saveAllMetadata()
        _files.remove(file.relativePath)
    }

    fun reload() {
        _files.clear()
        _appliedPatches.clear()
        _appliedPatches.addAll(loadAppliedPatches())
        scan()
    }

    fun applyPatch(patch: Patch, forceDelete: Boolean) {
        val entriesToApply = patch.entries.filter { shouldApply(it, forceDelete) }

        for (entry in entriesToApply.filter { it.optionPath == "" && it.kind == EntryKind.DELETION }) {
            val file = _files[Path.of(entry.filePath)]
            if (file != null) removeFile(file)
            else {
                rootPath.resolve(entry.filePath).toFile().delete()
                allMetadata.remove(entry.filePath)
            }
        }

        val jsonEntries = entriesToApply.filter { it.optionPath != "" }

        val mocFiles: Map<String, MocFile> = jsonEntries.map { it.filePath }.distinct()
            .associateWith { filePath ->
                val meta = patch.metadata[filePath] ?: emptyMap()
                MocFile.ensureWritable(
                    this, Path.of(filePath),
                    contentTypeId = meta["content"] ?: TextContentType.id,
                    metadata      = meta
                )
            }

        for ((filePath, entries) in jsonEntries.groupBy { it.filePath }) {
            val file = mocFiles[filePath] ?: continue
            var content = if (file.exists) file.getStringContent() ?: "{}" else "{}"
            for (entry in entries) {
                content = when (entry.kind) {
                    EntryKind.VALUE    -> setJsonValue(content, entry.optionPath, entry.toValue)
                    EntryKind.DELETION -> removeJsonKey(content, entry.optionPath)
                }
            }
            file.setContent(json5.parse(content))
        }

        if (patch.metadata.isNotEmpty()) {
            for ((fp, meta) in patch.metadata) allMetadata[fp] = meta.toMutableMap()
            saveAllMetadata()
        }

        _appliedPatches.add(patch.name)
        saveAppliedPatches()
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
        if (entry.optionPath == "$" || entry.optionPath == "") return hasFile(path)
        val file = _files[path] ?: return false
        return file.getFlatContent()?.containsKey(entry.optionPath) == true
    }

    private fun setJsonValue(content: String, optionPath: String, value: Any?): String {
        if (optionPath == "$") return gson.toJson(gson.toJsonTree(value))
        val config = Configuration.builder()
            .jsonProvider(GsonJsonProvider())
            .mappingProvider(GsonMappingProvider())
            .build()
        val document = JsonPath.using(config).parse(content)
        ensureAndSet(document, optionPath, gson.toJsonTree(value))
        return gson.toJson(document.read<JsonElement>("$"))
    }

    // Crée récursivement les noeuds intermédiaires manquants avant de setter la valeur.
    // Gère la notation bracket ($['a']['b']) et dot ($.a.b).
    private fun ensureAndSet(document: DocumentContext, path: String, value: JsonElement) {
        try { document.set(path, value); return } catch (_: PathNotFoundException) {}
        val (parentPath, key) = splitLastSegment(path) ?: return
        try { document.read<Any>(parentPath) } catch (_: PathNotFoundException) {
            ensureAndSet(document, parentPath, JsonObject())
        }
        try { document.put(parentPath, key, value) } catch (_: Exception) {}
    }

    private fun splitLastSegment(path: String): Pair<String, String>? {
        val lastBracket = path.lastIndexOf('[')
        val lastDot = path.lastIndexOf('.')
        return when {
            lastBracket > lastDot && lastBracket > 0 -> {
                val parent = path.substring(0, lastBracket)
                val key = path.substring(lastBracket + 1, path.length - 1).trim('\'', '"')
                if (parent.isNotEmpty()) parent to key else null
            }
            lastDot > 0 -> {
                val parent = path.substring(0, lastDot)
                val key = path.substring(lastDot + 1)
                if (parent.isNotEmpty()) parent to key else null
            }
            else -> null
        }
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

    private fun scan() {
        if (!rootPath.isDirectory()) return
        Files.walk(rootPath)
            .filter { file ->
                file.isRegularFile()
                    && file != metadataJsonFile
                    && file != appliedPatchesFile
                    && ignoredPaths.none { file.startsWith(rootPath.resolve(it)) }
                    && !MocFile.isBinary(file)
            }
            .forEach { MocFile.load(this, rootPath.relativize(it)) }
    }

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
        metadataJsonFile.parent?.toFile()?.mkdirs()
        metadataJsonFile.toFile().writeText(gson.toJson(allMetadata))
    }

    private fun loadAppliedPatches(): MutableList<String> {
        if (!appliedPatchesFile.toFile().exists()) return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<String>>() {}.type
            gson.fromJson(appliedPatchesFile.toFile().readText(), type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveAppliedPatches() {
        appliedPatchesFile.parent?.toFile()?.mkdirs()
        appliedPatchesFile.toFile().writeText(gson.toJson(_appliedPatches))
    }

    fun diffFrom(other: MocFileSystem): FileSystemDiff {
        val result = FileSystemDiff()

        for (path in other._files.keys - _files.keys) {
            val otherFile = other._files[path]!!
            val ghostCurrent = MocFile.ghost(this, path, otherFile.contentType.id, otherFile.metadata)
            result.addDeleted(path, ghostCurrent.diffFrom(otherFile))
        }
        for (path in _files.keys - other._files.keys) {
            val current = _files[path]!!
            val ghostRef = MocFile.ghost(other, path, current.contentType.id, current.metadata)
            result.addNew(path, current.diffFrom(ghostRef))
        }
        for (path in _files.keys intersect other._files.keys) {
            val contentDiff = _files[path]!!.diffFrom(other._files[path]!!)
            if (contentDiff.isNotEmpty()) result.addChanged(path, contentDiff)
        }

        return result
    }
}
