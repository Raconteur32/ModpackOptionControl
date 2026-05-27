package fr.raconteur.moc.versioning

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.filesystem.McInstanceRefMocFileSystem
import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Path

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

        val raw: List<PatchEntry> = try {
            val type = object : TypeToken<List<PatchEntry>>() {}.type
            gson.fromJson(file.readText(), type) ?: return
        } catch (_: Exception) { return }

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
        if (a == b) return true
        if (a is Number && b is Number) return a.toDouble() == b.toDouble()
        return a?.toString() == b?.toString()
    }

    fun setValueEntry(diff: OptionDiff.New, mode: PatchMode) =
        setValueEntry(diff.filePath, diff.path, diff.oldValue, diff.newValue, mode)

    fun setValueEntry(diff: OptionDiff.Changed, mode: PatchMode) =
        setValueEntry(diff.filePath, diff.path, diff.oldValue, diff.newValue, mode)

    private fun setValueEntry(filePath: String, optionPath: String, fromValue: Any?, toValue: Any?, mode: PatchMode) {
        val entry = PatchEntry(filePath, optionPath, fromValue, toValue, EntryKind.VALUE, mode)
        _entries.removeIf { it.filePath == entry.filePath && it.optionPath == entry.optionPath }
        _entries.add(entry)
        save()
    }

    fun setDeletionEntry(diff: OptionDiff.Deleted, mode: PatchMode) {
        val entry = PatchEntry(diff.filePath, diff.path, diff.oldValue, null, EntryKind.DELETION, mode)
        _entries.removeIf { it.filePath == entry.filePath && it.optionPath == entry.optionPath }
        _entries.add(entry)
        save()
    }

    fun removeEntry(filePath: String, optionPath: String) {
        _entries.removeIf { it.filePath == filePath && it.optionPath == optionPath }
        save()
    }

    fun save() {
        val file = draftPath.toFile()
        file.parentFile.mkdirs()
        file.writeText(gson.toJson(_entries))
    }

    fun clear() {
        _entries.clear()
        save()
    }

    fun finalize(patchName: String): Patch {
        val dest = PlatformService.INSTANCE.getConfigDir().resolve("moc/patchs/$patchName.json").toFile()
        dest.parentFile.mkdirs()
        dest.writeText(gson.toJson(_entries))
        val patch = Patch(patchName, _entries.toList())
        PatchList.add(patchName)
        McInstanceRefMocFileSystem.applyPatch(patch, forceDelete = true)
        clear()
        return patch
    }
}
