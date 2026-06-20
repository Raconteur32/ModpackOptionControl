package fr.raconteur.moc.filesystem

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import de.marhali.json5.Json5Element
import de.marhali.json5.Json5Object
import fr.raconteur.moc.content.TextContentType
import fr.raconteur.moc.content.anyToJson5Element
import fr.raconteur.moc.versioning.EntryKind
import fr.raconteur.moc.versioning.Patch
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchList
import fr.raconteur.moc.versioning.PatchMode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

open class MocFileSystem(
    private val rootPath: Path,
    private val ignoredPaths: List<Path> = emptyList(),
    hasRef: Boolean = false,
    private val onRefError: (patchName: String, e: Exception) -> Unit = { _, _ -> }
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val metasDir: Path             = rootPath.resolve("mocfsmetas")
    private val metadataJsonFile: Path     = metasDir.resolve("mocmetadata.json")
    private val appliedPatchesFile: Path   = metasDir.resolve("mocappliedpatches.json")
    private val appliedLogsDir: Path       = metasDir.resolve("mocappliedlogs")
    private val allMetadata: MutableMap<String, MutableMap<String, String>> = loadAllMetadata()

    private val _files: MutableMap<Path, MocFile> = mutableMapOf()
    val files: Collection<MocFile> get() = _files.values

    private val _appliedPatches: MutableList<String> = loadAppliedPatches()
    val appliedPatches: List<String> get() = _appliedPatches.toList()

    private var ref: MocFileSystem? = null

    init {
        scan()
        if (hasRef) {
            val refPath = metasDir.resolve("ref")
            refPath.toFile().deleteRecursively()
            val newRef = MocFileSystem(refPath)
            newRef.applyMultiplePatches(_appliedPatches.toList(), onError = onRefError)
            ref = newRef
        }
    }

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
        ref?.reload()
    }

    fun applyPatch(patch: Patch, forceOverride: Boolean = false) {
        val refDiff: FileSystemDiff? = ref?.let { diffFrom(it) }
        val entriesToApply = patch.entries.filter { shouldApply(it, refDiff, forceOverride) }

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
            var element: Json5Element = if (file.exists) file.getContent() ?: Json5Object() else Json5Object()
            for (entry in entries) {
                element = when (entry.kind) {
                    EntryKind.VALUE    -> setJson5Value(element, entry.optionPath, entry.toValue)
                    EntryKind.DELETION -> removeJson5Key(element, entry.optionPath)
                }
            }
            file.setContent(element)
        }

        if (patch.metadata.isNotEmpty()) {
            for ((fp, meta) in patch.metadata) allMetadata[fp] = meta.toMutableMap()
            saveAllMetadata()
        }

        _appliedPatches.add(patch.name)
        saveAppliedPatches()
        writeApplicationLog(patch.name, entriesToApply)
        ref?.applyPatch(patch, forceOverride = true)
    }

    private fun writeApplicationLog(patchName: String, entries: List<PatchEntry>) {
        val array = JsonArray()
        for (entry in entries) {
            val obj = JsonObject()
            obj.addProperty("kind", entry.kind.name.lowercase())
            obj.addProperty("file", entry.filePath)
            obj.addProperty("path", entry.optionPath.ifEmpty { "(file)" })
            obj.add("from", gson.toJsonTree(entry.fromValue))
            if (entry.kind == EntryKind.VALUE) obj.add("to", gson.toJsonTree(entry.toValue))
            array.add(obj)
        }
        appliedLogsDir.toFile().mkdirs()
        appliedLogsDir.resolve("applied.$patchName.json5").toFile().writeText(gson.toJson(array))
    }

    private fun shouldApply(entry: PatchEntry, refDiff: FileSystemDiff?, forceOverride: Boolean): Boolean =
        when (entry.mode) {
            PatchMode.OVERRIDE -> true
            PatchMode.DEFAULT  -> when (entry.kind) {
                EntryKind.VALUE    -> !entryExists(entry) || matchesRef(entry, refDiff) || forceOverride
                EntryKind.DELETION -> matchesRef(entry, refDiff) || forceOverride
            }
        }

    private fun matchesRef(entry: PatchEntry, refDiff: FileSystemDiff?): Boolean {
        if (refDiff == null) return false
        val fileDiff = refDiff[Path.of(entry.filePath)] ?: return true
        return !fileDiff.flatContentDiff.containsKey(entry.optionPath)
    }

    private fun entryExists(entry: PatchEntry): Boolean {
        val path = Path.of(entry.filePath)
        if (entry.optionPath == "$" || entry.optionPath == "") return hasFile(path)
        val file = _files[path] ?: return false
        return file.getFlatContent()?.containsKey(entry.optionPath) == true
    }

    private fun setJson5Value(element: Json5Element, optionPath: String, value: Any?): Json5Element {
        if (optionPath == "$") return anyToJson5Element(value)
        val config = Configuration.builder()
            .jsonProvider(Json5JsonProvider())
            .mappingProvider(Json5MappingProvider())
            .build()
        val document = JsonPath.using(config).parse(element as Any)
        ensureAndSet(document, optionPath, anyToJson5Element(value))
        return document.read<Json5Element>("$")
    }

    // Crée récursivement les noeuds intermédiaires manquants avant de setter la valeur.
    // Gère la notation bracket ($['a']['b']) et dot ($.a.b).
    private fun ensureAndSet(document: DocumentContext, path: String, value: Json5Element) {
        try { document.set(path, value); return } catch (_: PathNotFoundException) {}
        val (parentPath, key) = splitLastSegment(path) ?: return
        try { document.read<Any>(parentPath) } catch (_: PathNotFoundException) {
            ensureAndSet(document, parentPath, Json5Object())
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

    private fun removeJson5Key(element: Json5Element, optionPath: String): Json5Element {
        val config = Configuration.builder()
            .jsonProvider(Json5JsonProvider())
            .mappingProvider(Json5MappingProvider())
            .build()
        val document = JsonPath.using(config).parse(element as Any)
        try { document.delete(optionPath) } catch (_: Exception) {}
        return document.read<Json5Element>("$")
    }

    private fun scan() {
        if (!rootPath.isDirectory()) return
        Files.walk(rootPath)
            .filter { file ->
                file.isRegularFile()
                    && !file.startsWith(metasDir)
                    && ignoredPaths.none { file.startsWith(rootPath.resolve(it)) }
                    && !MocFileInspector.isBinary(file)
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

    fun applyPending(
        onApplied: (patchName: String) -> Unit = {},
        onError:   (patchName: String, e: Exception) -> Unit = { _, _ -> }
    ) {
        val applied = appliedPatches.toSet()
        val toApply = PatchList.getAll().filter { it !in applied }
        if (toApply.isEmpty()) return
        applyMultiplePatches(toApply, onApplied, onError)
    }

    fun applyMultiplePatches(
        patches: List<String>,
        onApplied: (patchName: String) -> Unit = {},
        onError:   (patchName: String, e: Exception) -> Unit = { _, _ -> }
    ) {
        for (patchName in patches) {
            try {
                applyPatch(Patch.load(patchName))
                onApplied(patchName)
            } catch (e: Exception) {
                onError(patchName, e)
                break
            }
        }
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
