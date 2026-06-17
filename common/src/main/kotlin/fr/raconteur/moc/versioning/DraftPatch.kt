package fr.raconteur.moc.versioning

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import de.marhali.json5.Json5Element
import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.filesystem.McInstanceRefMocFileSystem
import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Path
import java.nio.file.Paths

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

    private fun json5ToNative(value: Any?): Any? = when (value) {
        is Json5Element -> when {
            value.isJson5Null      -> null
            value.isJson5Primitive -> value.asJson5Primitive.let { p ->
                when {
                    p.isBoolean -> p.asBoolean
                    p.isNumber  -> p.asNumber
                    else        -> p.asString
                }
            }
            value.isJson5Object -> value.asJson5Object.entrySet()
                .associate { (k, v) -> k to json5ToNative(v) }
            value.isJson5Array  -> value.asJson5Array.asList()
                .map { json5ToNative(it) }
            else -> null
        }
        else -> value
    }

    fun setValueEntry(diff: OptionDiff.New, mode: PatchMode) =
        setValueEntry(diff.filePath, diff.path, diff.oldValue, diff.newValue, mode)

    fun setValueEntry(diff: OptionDiff.Changed, mode: PatchMode) =
        setValueEntry(diff.filePath, diff.path, diff.oldValue, diff.newValue, mode)

    private fun setValueEntry(filePath: String, optionPath: String, fromValue: Any?, toValue: Any?, mode: PatchMode) {
        val entry = PatchEntry(filePath, optionPath, json5ToNative(fromValue), json5ToNative(toValue), EntryKind.VALUE, mode)
        _entries.removeIf { it.filePath == entry.filePath && it.optionPath == entry.optionPath }
        _entries.add(entry)
        save()
    }

    fun setDeletionEntry(diff: OptionDiff.Deleted, mode: PatchMode) {
        val entry = PatchEntry(diff.filePath, diff.path, json5ToNative(diff.oldValue), null, EntryKind.DELETION, mode)
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
        file.writeText(_entries.toJson5String())
    }

    fun clear() {
        _entries.clear()
        save()
    }

    fun finalize(patchName: String): Patch {
        require(!PatchList.contains(patchName)) { "Patch « $patchName » already exists" }
        val dir = PlatformService.INSTANCE.getConfigDir().resolve("moc/patchs/$patchName")
        dir.toFile().mkdirs()

        dir.resolve("patch.json").toFile().writeText(_entries.toJson5String())

        val patchFilePaths = _entries.map { it.filePath }.toSet()
        val metaType = object : TypeToken<Map<String, Map<String, String>>>() {}.type
        val allMeta: Map<String, Map<String, String>> = try {
            gson.fromJson(McInstanceMocFileSystem.getMetadataFile().toFile().readText(), metaType) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }
        val filteredMeta = allMeta.filter { (key, _) -> key in patchFilePaths }
        dir.resolve("mocmeta.json").toFile().writeText(gson.toJson(filteredMeta))

        val patch = Patch(patchName, _entries.toList(), filteredMeta)
        PatchList.add(patchName)
        McInstanceRefMocFileSystem.applyPatch(patch, forceDelete = true)
        clear()
        return patch
    }
}
