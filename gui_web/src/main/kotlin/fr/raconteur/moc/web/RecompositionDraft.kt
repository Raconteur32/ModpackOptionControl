package fr.raconteur.moc.web

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.MocFileDiff
import fr.raconteur.moc.filesystem.MocFileSystem
import fr.raconteur.moc.filesystem.isDescendant
import fr.raconteur.moc.platform.PlatformService
import fr.raconteur.moc.versioning.EntryKind
import fr.raconteur.moc.versioning.Patch
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchList
import fr.raconteur.moc.versioning.PatchMode
import fr.raconteur.moc.versioning.json5ToNative
import fr.raconteur.moc.versioning.parsePatchEntries
import fr.raconteur.moc.versioning.toJson5String
import java.nio.file.Path

// Web-module copy of the recomposition session state (extracted from common; see
// openspec/changes/extract-authoring-from-common). Reads/writes the same on-disk
// state as the desktop gui's copy so both tools stay interchangeable on an instance.
object RecompositionDraft {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val draftPath: Path
        get() = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/recomposition-draft.json")
    private val beforePath: Path
        get() = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/recomp-before")
    private val afterPath: Path
        get() = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/recomp-after")
    private val amendPath: Path
        get() = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/amend")

    var rangeStart: Int? = null
        private set
    var rangeEnd: Int? = null
        private set
    var isAmend: Boolean = false
        private set

    private val _entries: MutableList<PatchEntry> = mutableListOf()
    val entries: List<PatchEntry> get() = _entries

    private val _conflictingEntries: MutableSet<Pair<String, String>> = mutableSetOf()
    val conflictingEntries: Set<Pair<String, String>> get() = _conflictingEntries.toSet()

    // Maps (filePath to optionPath) → source patch name for auto-populated entries
    private val _sourceMap: MutableMap<Pair<String, String>, String> = mutableMapOf()
    val sourceMap: Map<Pair<String, String>, String> get() = _sourceMap

    var cachedDiff: List<Map.Entry<Path, MocFileDiff>> = emptyList()
        private set

    init { load() }

    fun hasActiveDraft(): Boolean = rangeStart != null

    fun build(startIdx: Int, endIdx: Int, isAmend: Boolean = false) {
        rangeStart    = startIdx
        rangeEnd      = endIdx
        this.isAmend  = isAmend
        _entries.clear()
        save()
        rebuildDiff()
        autoPopulateDraft()
        save()
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

        if (isAmend && amendPath.toFile().exists()) {
            afterFS.applyPatch(Patch.loadFromDir(amendPath, "amend"), forceOverride = true)
        }

        cachedDiff = afterFS.diffFrom(beforeFS).entries
            .sortedBy { it.key.toString() }
            .toList()

        validateEntries()
    }

    private fun autoPopulateDraft() {
        val start = rangeStart ?: return
        val end   = rangeEnd   ?: return
        val allPatches = PatchList.getAll()

        // Collect all entries from every patch in the range (+ amend patch if applicable),
        // tracking the source patch name for each entry
        val rangeEntries: List<Pair<String, PatchEntry>> = buildList {
            allPatches.subList(start, end + 1).forEach { patchName ->
                Patch.load(patchName).entries.forEach { add(patchName to it) }
            }
            if (isAmend && amendPath.toFile().exists()) {
                Patch.loadFromDir(amendPath, "amend").entries.forEach { add("amend" to it) }
            }
        }

        // Mark as conflicting any pair that shares the same file and overlaps with another
        _conflictingEntries.clear()
        val conflicting = _conflictingEntries
        for (i in rangeEntries.indices) {
            val (_, entry1) = rangeEntries[i]
            val (fp1, op1)  = entry1.filePath to entry1.optionPath
            for (j in rangeEntries.indices) {
                if (i == j) continue
                val (_, entry2) = rangeEntries[j]
                val (fp2, op2)  = entry2.filePath to entry2.optionPath
                if (fp1 != fp2) continue
                if (op1 == op2 || isDescendant(op1, op2) || isDescendant(op2, op1)) {
                    conflicting.add(fp1 to op1)
                    conflicting.add(fp2 to op2)
                }
            }
        }

        // Auto-populate draft with non-conflicting entries, using the net diff value
        // and preserving the original mode from the patch entry
        _sourceMap.clear()
        val diffMap = cachedDiff.associate { it.key.toString() to it.value }
        val seen    = mutableSetOf<Pair<String, String>>()
        for ((sourceName, entry) in rangeEntries) {
            val key = entry.filePath to entry.optionPath
            if (key in conflicting || !seen.add(key)) continue
            val optDiff = diffMap[entry.filePath]?.flatContentDiff?.get(entry.optionPath) ?: continue
            // applyDiff() -> setValueEntry()/setDeletionEntry() removes any existing
            // _sourceMap entry for this key as a side effect (correct for manual staging,
            // where a freshly-staged entry has no patch provenance) — so it must run
            // BEFORE recording the source here, not after, or the source is immediately wiped.
            applyDiff(optDiff, entry.mode)
            _sourceMap[key] = sourceName
        }
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

    // No-overlap invariant (see DraftPatch): staging replaces staged ancestors and
    // descendants in the same file; their auto-population provenance goes with them.
    private fun removeOverlapping(filePath: String, optionPath: String) {
        _entries.removeIf {
            it.filePath == filePath && it.optionPath != optionPath &&
                (isDescendant(it.optionPath, optionPath) || isDescendant(optionPath, it.optionPath))
        }
        _sourceMap.keys.removeIf { (fp, op) ->
            fp == filePath && op != optionPath &&
                (isDescendant(op, optionPath) || isDescendant(optionPath, op))
        }
    }

    fun setValueEntry(filePath: String, optionPath: String, fromValue: Any?, toValue: Any?, mode: PatchMode) {
        val entry = PatchEntry(filePath, optionPath, json5ToNative(fromValue), json5ToNative(toValue), EntryKind.VALUE, mode)
        val key   = filePath to optionPath
        removeOverlapping(filePath, optionPath)
        _entries.removeIf { it.filePath == filePath && it.optionPath == optionPath }
        _sourceMap.remove(key)
        _entries.add(entry)
        save()
    }

    fun setDeletionEntry(filePath: String, optionPath: String, oldValue: Any?, mode: PatchMode) {
        val entry = PatchEntry(filePath, optionPath, json5ToNative(oldValue), null, EntryKind.DELETION, mode)
        val key   = filePath to optionPath
        removeOverlapping(filePath, optionPath)
        _entries.removeIf { it.filePath == filePath && it.optionPath == optionPath }
        _sourceMap.remove(key)
        _entries.add(entry)
        save()
    }

    fun removeEntry(filePath: String, optionPath: String) {
        _entries.removeIf { it.filePath == filePath && it.optionPath == optionPath }
        _sourceMap.remove(filePath to optionPath)
        save()
    }

    fun entryFor(filePath: String, optionPath: String): PatchEntry? =
        _entries.find { it.filePath == filePath && it.optionPath == optionPath }

    // Marks an inter-patch conflict as resolved (either by staging one of the options
    // manually or by ignoring it for this recomposition session). No persistence needed:
    // conflictingEntries/sourceMap are rebuilt from scratch on every build()/rebuildDiff().
    fun resolveConflict(filePath: String, optionPath: String) {
        _conflictingEntries.remove(filePath to optionPath)
    }

    fun clear() {
        val wasAmend = isAmend
        rangeStart   = null
        rangeEnd     = null
        isAmend      = false
        _entries.clear()
        _conflictingEntries.clear()
        _sourceMap.clear()
        cachedDiff = emptyList()
        draftPath.toFile().delete()
        beforePath.toFile().deleteRecursively()
        afterPath.toFile().deleteRecursively()
        if (wasAmend) amendPath.toFile().deleteRecursively()
    }

    fun finalize(patchName: String) {
        val start = rangeStart ?: error("No active recomposition")
        val end   = rangeEnd   ?: error("No active recomposition")

        val patchesRoot = PatchList.patchesRoot()

        // Write to a temp dir so range deletion (which may include patchName) can't clobber the new patch
        val tempDir = patchesRoot.resolve("temp-$patchName").toFile()
        tempDir.deleteRecursively()
        tempDir.mkdirs()
        tempDir.resolve("patch.json").writeText(_entries.toJson5String())

        // Collect file-type metadata from the afterFS metadata file
        val metaType = object : com.google.gson.reflect.TypeToken<Map<String, Map<String, String>>>() {}.type
        val metaFile = afterPath.resolve("mocfsmetas/mocmetadata.json").toFile()
        val allMeta: Map<String, Map<String, String>> = try {
            if (metaFile.exists()) gson.fromJson(metaFile.readText(), metaType) ?: emptyMap()
            else emptyMap()
        } catch (_: Exception) { emptyMap() }
        val patchFilePaths = _entries.map { it.filePath }.toSet()
        val filteredMeta = allMeta.filter { it.key in patchFilePaths }
        tempDir.resolve("mocmeta.json").writeText(gson.toJson(filteredMeta))

        // Update active patch list: replace range with the new patch
        val allNames = PatchList.getAll()
        val rangeNames = allNames.subList(start, end + 1).toList()
        val mutableNames = allNames.toMutableList()
        mutableNames.subList(start, end + 1).clear()
        mutableNames.add(start, patchName)
        PatchList.setAll(mutableNames)

        // Record range patches as deleted and remove their folders.
        // Skip addToDeleted for patchName itself — it was just re-added to the active list.
        rangeNames.forEach {
            if (it != patchName) PatchList.addToDeleted(it)
            PatchList.deleteFolder(it)
        }

        // Rename temp dir to final name now that the range is gone
        tempDir.renameTo(patchesRoot.resolve(patchName).toFile())

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
        obj.addProperty("is_amend", isAmend)
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
            isAmend    = obj.get("is_amend")?.asBoolean ?: false
            val raw = obj.get("entries_raw")?.asString ?: ""
            if (raw.isNotBlank()) parsePatchEntries(raw).forEach { _entries.add(it) }
            rebuildDiff()
        } catch (_: Exception) {
            clear()
        }
    }
}
