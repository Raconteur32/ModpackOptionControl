package fr.raconteur.moc.web

import com.google.gson.GsonBuilder
import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
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

// Web-module copy of the draft patch staging area (extracted from common; see
// openspec/changes/extract-authoring-from-common). Reads/writes the same on-disk
// state as the desktop gui's copy so both tools stay interchangeable on an instance.
object DraftPatch {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val draftPath: Path
        get() = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/patch-draft.json")

    private val _entries: MutableList<PatchEntry> = mutableListOf()
    val entries: List<PatchEntry> get() = _entries

    init {
        loadAndValidate()
    }

    private fun loadAndValidate() {
        _entries.clear()
        val file = draftPath.toFile()
        if (!file.exists()) return

        val currentDiff = McInstanceMocFileSystem.diffFrom(McInstanceRefMocFileSystem)

        val raw = parsePatchEntries(file.readText())

        raw.filter { entry ->
            val fileDiff = currentDiff[Path.of(entry.filePath)] ?: return@filter false
            val liveOption = fileDiff.flatContentDiff[entry.optionPath] ?: return@filter false
            when (entry.kind) {
                EntryKind.VALUE    -> valueEquals(liveOption.newValue, entry.toValue)
                                   && valueEquals(liveOption.oldValue, entry.fromValue)
                EntryKind.DELETION -> liveOption is OptionDiff.Deleted
            }
        }.forEach { _entries.add(it) }

        save()
    }

    private fun valueEquals(a: Any?, b: Any?): Boolean {
        val na = json5ToNative(a)
        val nb = json5ToNative(b)
        if (na == nb) return true
        if (na is Number && nb is Number) return try {
            java.math.BigDecimal(na.toString()).compareTo(java.math.BigDecimal(nb.toString())) == 0
        } catch (_: Exception) { false }
        if (na is Map<*, *> && nb is Map<*, *>)
            return na.size == nb.size && na.keys == nb.keys && na.keys.all { k -> valueEquals(na[k], nb[k]) }
        if (na is List<*> && nb is List<*>)
            return na.size == nb.size && na.indices.all { i -> valueEquals(na[i], nb[i]) }
        return false
    }

    fun setValueEntry(diff: OptionDiff.New, mode: PatchMode) =
        setValueEntry(diff.filePath, diff.path, diff.oldValue, diff.newValue, mode)

    fun setValueEntry(diff: OptionDiff.Changed, mode: PatchMode) =
        setValueEntry(diff.filePath, diff.path, diff.oldValue, diff.newValue, mode)

    // No-overlap invariant: staging a path replaces staged ancestors and descendants in
    // the same file (the file-deletion marker "" never overlaps — delete-then-recreate
    // compositions stay possible). Server-side so it holds for any client, atomically.
    private fun removeOverlapping(filePath: String, optionPath: String) {
        _entries.removeIf {
            it.filePath == filePath && it.optionPath != optionPath &&
                (isDescendant(it.optionPath, optionPath) || isDescendant(optionPath, it.optionPath))
        }
    }

    private fun setValueEntry(filePath: String, optionPath: String, fromValue: Any?, toValue: Any?, mode: PatchMode) {
        val entry = PatchEntry(filePath, optionPath, json5ToNative(fromValue), json5ToNative(toValue), EntryKind.VALUE, mode)
        removeOverlapping(entry.filePath, entry.optionPath)
        _entries.removeIf { it.filePath == entry.filePath && it.optionPath == entry.optionPath }
        _entries.add(entry)
        save()
    }

    fun setDeletionEntry(diff: OptionDiff.Deleted, mode: PatchMode) {
        val entry = PatchEntry(diff.filePath, diff.path, json5ToNative(diff.oldValue), null, EntryKind.DELETION, mode)
        removeOverlapping(entry.filePath, entry.optionPath)
        _entries.removeIf { it.filePath == entry.filePath && it.optionPath == entry.optionPath }
        _entries.add(entry)
        save()
    }

    fun removeEntry(filePath: String, optionPath: String) {
        _entries.removeIf { it.filePath == filePath && it.optionPath == optionPath }
        save()
    }

    fun removeEntriesForFile(filePath: String) {
        _entries.removeIf { it.filePath == filePath }
        save()
    }

    fun removeEntriesUnder(dir: String) {
        val prefix = if (dir.endsWith("/")) dir else "$dir/"
        _entries.removeIf { it.filePath.startsWith(prefix) }
        save()
    }

    fun save() {
        val file = draftPath.toFile()
        file.parentFile.mkdirs()
        file.writeText(_entries.toJson5String())
    }

    fun clear() {
        _entries.clear()
        save()
    }

    private val amendDir: Path
        get() = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/amend")

    fun finalizeForAmend(): Int {
        val allPatches = PatchList.getAll()
        require(allPatches.isNotEmpty()) { "No patches to amend against" }
        val lastIdx = allPatches.size - 1

        val dir = amendDir.toFile()
        dir.deleteRecursively()
        dir.mkdirs()
        dir.resolve("patch.json").writeText(_entries.toJson5String())

        val patchFilePaths = _entries.map { it.filePath }.toSet()
        val filteredMeta = McInstanceMocFileSystem.effectiveMetadataFor(patchFilePaths)
        dir.resolve("mocmeta.json").writeText(gson.toJson(filteredMeta))

        clear()
        return lastIdx
    }

    fun finalize(patchName: String): Patch {
        require(!PatchList.contains(patchName)) { "Patch « $patchName » already exists" }
        val dir = PatchList.patchesRoot().resolve(patchName)
        dir.toFile().mkdirs()

        dir.resolve("patch.json").toFile().writeText(_entries.toJson5String())

        val patchFilePaths = _entries.map { it.filePath }.toSet()
        val filteredMeta = McInstanceMocFileSystem.effectiveMetadataFor(patchFilePaths)
        dir.resolve("mocmeta.json").toFile().writeText(gson.toJson(filteredMeta))

        val patch = Patch(patchName, _entries.toList(), filteredMeta)
        PatchList.add(patchName)
        McInstanceRefMocFileSystem.applyPatch(patch, forceOverride = true)
        clear()
        return patch
    }
}
