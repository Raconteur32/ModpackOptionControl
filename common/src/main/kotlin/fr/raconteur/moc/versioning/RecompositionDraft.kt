package fr.raconteur.moc.versioning

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.McInstanceRefMocFileSystem
import fr.raconteur.moc.filesystem.MocFileDiff
import fr.raconteur.moc.filesystem.MocFileSystem
import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Path

object RecompositionDraft {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val draftPath: Path
        get() = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/recomposition-draft.json")
    private val beforePath: Path
        get() = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/recomp-before")
    private val afterPath: Path
        get() = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/recomp-after")

    var rangeStart: Int? = null
        private set
    var rangeEnd: Int? = null
        private set

    private val _entries: MutableList<PatchEntry> = mutableListOf()
    val entries: List<PatchEntry> get() = _entries

    var cachedDiff: List<Map.Entry<Path, MocFileDiff>> = emptyList()
        private set

    init { load() }

    fun hasActiveDraft(): Boolean = rangeStart != null

    fun build(startIdx: Int, endIdx: Int) {
        rangeStart = startIdx
        rangeEnd   = endIdx
        _entries.clear()
        save()
        rebuildDiff()
    }

    private fun rebuildDiff() {
        val start = rangeStart ?: return
        val end   = rangeEnd   ?: return
        val allPatches = PatchList.getAll()

        if (start > allPatches.size || end >= allPatches.size) {
            clear(); return
        }

        beforePath.toFile().deleteRecursively()
        afterPath.toFile().deleteRecursively()

        val beforeFS = MocFileSystem(beforePath)
        allPatches.subList(0, start).forEach { beforeFS.applyPatch(Patch.load(it), forceOverride = true) }

        val afterFS = MocFileSystem(afterPath)
        allPatches.subList(0, end + 1).forEach { afterFS.applyPatch(Patch.load(it), forceOverride = true) }

        cachedDiff = afterFS.diffFrom(beforeFS).entries
            .sortedBy { it.key.toString() }
            .toList()

        validateEntries()
    }

    private fun validateEntries() {
        val diffMap = cachedDiff.associate { it.key.toString() to it.value }
        val valid = _entries.filter { entry ->
            val fileDiff = diffMap[entry.filePath] ?: return@filter false
            val optDiff  = fileDiff.flatContentDiff[entry.optionPath] ?: return@filter false
            when (entry.kind) {
                EntryKind.VALUE    -> optDiff is OptionDiff.New || optDiff is OptionDiff.Changed
                EntryKind.DELETION -> optDiff is OptionDiff.Deleted
            }
        }
        if (valid.size != _entries.size) {
            _entries.clear()
            _entries.addAll(valid)
            save()
        }
    }

    fun applyDiff(optDiff: OptionDiff?, mode: PatchMode) = when (optDiff) {
        is OptionDiff.New     -> setValueEntry(optDiff.filePath, optDiff.path, optDiff.oldValue, optDiff.newValue, mode)
        is OptionDiff.Changed -> setValueEntry(optDiff.filePath, optDiff.path, optDiff.oldValue, optDiff.newValue, mode)
        is OptionDiff.Deleted -> setDeletionEntry(optDiff.filePath, optDiff.path, optDiff.oldValue, mode)
        null                  -> Unit
    }

    fun setValueEntry(filePath: String, optionPath: String, fromValue: Any?, toValue: Any?, mode: PatchMode) {
        val entry = PatchEntry(filePath, optionPath, json5ToNative(fromValue), json5ToNative(toValue), EntryKind.VALUE, mode)
        _entries.removeIf { it.filePath == filePath && it.optionPath == optionPath }
        _entries.add(entry)
        save()
    }

    fun setDeletionEntry(filePath: String, optionPath: String, oldValue: Any?, mode: PatchMode) {
        val entry = PatchEntry(filePath, optionPath, json5ToNative(oldValue), null, EntryKind.DELETION, mode)
        _entries.removeIf { it.filePath == filePath && it.optionPath == optionPath }
        _entries.add(entry)
        save()
    }

    fun removeEntry(filePath: String, optionPath: String) {
        _entries.removeIf { it.filePath == filePath && it.optionPath == optionPath }
        save()
    }

    fun entryFor(filePath: String, optionPath: String): PatchEntry? =
        _entries.find { it.filePath == filePath && it.optionPath == optionPath }

    fun clear() {
        rangeStart = null
        rangeEnd   = null
        _entries.clear()
        cachedDiff = emptyList()
        draftPath.toFile().delete()
        beforePath.toFile().deleteRecursively()
        afterPath.toFile().deleteRecursively()
    }

    fun finalize(patchName: String) {
        val start = rangeStart ?: error("No active recomposition")
        val end   = rangeEnd   ?: error("No active recomposition")

        val dir = PlatformService.INSTANCE.getConfigDir().resolve("moc/patchs/$patchName")
        dir.toFile().mkdirs()
        dir.resolve("patch.json").toFile().writeText(_entries.toJson5String())

        // Collect file-type metadata from the afterFS metadata file
        val metaType = object : TypeToken<Map<String, Map<String, String>>>() {}.type
        val metaFile = afterPath.resolve("mocfsmetas/mocmetadata.json").toFile()
        val allMeta: Map<String, Map<String, String>> = try {
            if (metaFile.exists()) gson.fromJson(metaFile.readText(), metaType) ?: emptyMap()
            else emptyMap()
        } catch (_: Exception) { emptyMap() }
        val patchFilePaths = _entries.map { it.filePath }.toSet()
        val filteredMeta = allMeta.filter { it.key in patchFilePaths }
        dir.resolve("mocmeta.json").toFile().writeText(gson.toJson(filteredMeta))

        // Update active patch list: replace range with the new patch
        val allNames = PatchList.getAll().toMutableList()
        val rangeNames = allNames.subList(start, end + 1).toList()
        allNames.subList(start, end + 1).clear()
        allNames.add(start, patchName)
        PatchList.setAll(allNames)

        // Record range patches as deleted and remove their folders
        rangeNames.forEach {
            PatchList.addToDeleted(it)
            PatchList.deleteFolder(it)
        }

        McInstanceRefMocFileSystem.regenerateRefFiles()

        clear()
    }

    fun save() {
        val start = rangeStart ?: return
        val file = draftPath.toFile()
        file.parentFile.mkdirs()
        val obj = JsonObject()
        obj.addProperty("range_start", start)
        obj.addProperty("range_end", rangeEnd)
        obj.addProperty("entries_raw", _entries.toJson5String())
        file.writeText(gson.toJson(obj))
    }

    private fun load() {
        val file = draftPath.toFile()
        if (!file.exists()) return
        try {
            val obj = gson.fromJson(file.readText(), JsonObject::class.java) ?: return
            rangeStart = obj.get("range_start")?.asInt ?: return
            rangeEnd   = obj.get("range_end")?.asInt   ?: return
            val raw = obj.get("entries_raw")?.asString ?: ""
            if (raw.isNotBlank()) parsePatchEntries(raw).forEach { _entries.add(it) }
            rebuildDiff()
        } catch (_: Exception) {
            clear()
        }
    }
}
