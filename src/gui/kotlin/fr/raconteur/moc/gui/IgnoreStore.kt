package fr.raconteur.moc.gui

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Path

enum class IgnoreKind { Session, Value, Permanent }

data class IgnoreEntry(
    @SerializedName("file_path")    val filePath: String,
    @SerializedName("option_path")  val optionPath: String,
    @SerializedName("target_value") val targetValue: String? = null
)

private data class EditorData(
    @SerializedName("value_ignores")     val valueIgnores: List<IgnoreEntry> = emptyList(),
    @SerializedName("permanent_ignores") val permanentIgnores: List<IgnoreEntry> = emptyList()
)

object IgnoreStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val editorPath: Path
        get() = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/editor.json")

    private val _sessionIgnores   = mutableListOf<IgnoreEntry>()
    private val _valueIgnores     = mutableListOf<IgnoreEntry>()
    private val _permanentIgnores = mutableListOf<IgnoreEntry>()

    val sessionIgnores:   List<IgnoreEntry> get() = _sessionIgnores.toList()
    val valueIgnores:     List<IgnoreEntry> get() = _valueIgnores.toList()
    val permanentIgnores: List<IgnoreEntry> get() = _permanentIgnores.toList()

    init { load() }

    fun add(entry: IgnoreEntry, kind: IgnoreKind) {
        listFor(kind).apply {
            removeIf { it.filePath == entry.filePath && it.optionPath == entry.optionPath }
            add(entry)
        }
        if (kind != IgnoreKind.Session) save()
    }

    fun remove(filePath: String, optionPath: String, kind: IgnoreKind) {
        listFor(kind).removeIf { it.filePath == filePath && it.optionPath == optionPath }
        if (kind != IgnoreKind.Session) save()
    }

    fun resetSession() { _sessionIgnores.clear() }

    fun isIgnored(filePath: String, optionPath: String, newValue: Any?): Boolean {
        if (_sessionIgnores.any   { it.filePath == filePath && it.optionPath == optionPath }) return true
        if (_permanentIgnores.any { it.filePath == filePath && it.optionPath == optionPath }) return true
        val newStr = newValue?.toString()
        return _valueIgnores.any { it.filePath == filePath && it.optionPath == optionPath && it.targetValue == newStr }
    }

    private fun listFor(kind: IgnoreKind): MutableList<IgnoreEntry> = when (kind) {
        IgnoreKind.Session   -> _sessionIgnores
        IgnoreKind.Value     -> _valueIgnores
        IgnoreKind.Permanent -> _permanentIgnores
    }

    private fun load() {
        val file = editorPath.toFile()
        if (!file.exists()) return
        val data = try { gson.fromJson(file.readText(), EditorData::class.java) } catch (_: Exception) { return }
        data?.valueIgnores?.forEach     { _valueIgnores.add(it) }
        data?.permanentIgnores?.forEach { _permanentIgnores.add(it) }
    }

    private fun save() {
        val file = editorPath.toFile()
        file.parentFile.mkdirs()
        file.writeText(gson.toJson(EditorData(_valueIgnores.toList(), _permanentIgnores.toList())))
    }
}
