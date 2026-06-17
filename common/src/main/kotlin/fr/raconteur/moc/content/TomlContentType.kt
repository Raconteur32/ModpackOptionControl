package fr.raconteur.moc.content

import com.electronwill.nightconfig.core.Config
import com.electronwill.nightconfig.core.UnmodifiableConfig
import com.electronwill.nightconfig.toml.TomlFormat
import com.electronwill.nightconfig.toml.TomlWriter
import de.marhali.json5.Json5Array
import de.marhali.json5.Json5Element
import de.marhali.json5.Json5Object
import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.filesystem.MocFile
import java.io.StringReader
import java.io.StringWriter
import java.util.Collections
import java.util.IdentityHashMap

object TomlContentType : ContentType() {
    override val id = "toml"

    override fun hasPreferredExtension(filename: String) = filename.endsWith(".toml")

    override fun hasValidContent(file: MocFile): Boolean {
        val text = file.getStringContent() ?: return false
        if (text.isBlank()) return false
        return try { parse(text); true } catch (_: Exception) { false }
    }

    override fun getContent(file: MocFile): Json5Element? {
        val text = file.getStringContent() ?: return null
        return try { configToJson5(parse(text)) } catch (_: Exception) { null }
    }

    override fun getSpecificMetadata(file: MocFile): Map<String, String> {
        val text = file.getStringContent() ?: return emptyMap()
        val paths = scanInlinePaths(text)
        return if (paths.isEmpty()) emptyMap()
               else mapOf("inline_tables" to paths.joinToString(","))
    }

    override fun setContent(file: MocFile, content: Json5Element) {
        require(content.isJson5Object) { "TOML root must be an object" }
        val inlinePaths = file.metadata["inline_tables"]
            ?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

        // Identity-keyed set: the same Config instances we register here are what
        // TomlWriter will test against via the predicate.
        val inlineConfigs: MutableSet<Config> = Collections.newSetFromMap(IdentityHashMap())
        val config = json5ToConfig(content.asJson5Object, emptyList(), inlinePaths, inlineConfigs)

        val writer = TomlWriter()
        writer.setWriteTableInlinePredicate { inlineConfigs.contains(it) }

        val sw = StringWriter()
        writer.write(config, sw)
        file.setStringContent(sw.toString())
    }

    // ── Parsing (TOML → Json5) ────────────────────────────────────────────────

    private fun parse(text: String): UnmodifiableConfig =
        TomlFormat.instance().createParser().parse(StringReader(text))

    private fun configToJson5(config: UnmodifiableConfig): Json5Object =
        Json5Object().also { obj ->
            for (entry in config.entrySet())
                obj.add(entry.key, anyToJson5(entry.getValue()))
        }

    private fun anyToJson5(value: Any?): Json5Element = when (value) {
        is Boolean            -> Json5Primitive.fromBoolean(value)
        is Number             -> Json5Primitive.fromNumber(value)
        is String             -> Json5Primitive.fromString(value)
        is UnmodifiableConfig -> configToJson5(value)
        is List<*>            -> Json5Array().also { arr -> value.forEach { arr.add(anyToJson5(it)) } }
        else                  -> Json5Primitive.fromString(value?.toString() ?: "")
    }

    // ── Inline-table detection (text scan) ────────────────────────────────────

    private val ARRAY_TABLE_RE = Regex("""^\[\[([^\]]+)]]""")
    private val TABLE_RE       = Regex("""^\[([^\]]+)]""")
    private val INLINE_KEY_RE  = Regex(
        """^((?:"[^"]*"|[A-Za-z0-9_\-]+)(?:\.(?:"[^"]*"|[A-Za-z0-9_\-]+))*)\s*=\s*\{"""
    )

    private fun scanInlinePaths(text: String): Set<String> {
        val result  = mutableSetOf<String>()
        val section = mutableListOf<String>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('#')) continue
            val arrayMatch = ARRAY_TABLE_RE.find(line)
            if (arrayMatch != null) {
                section.clear()
                section.addAll(parseKeySegments(arrayMatch.groupValues[1].trim()))
                continue
            }
            val tableMatch = TABLE_RE.find(line)
            if (tableMatch != null) {
                section.clear()
                section.addAll(parseKeySegments(tableMatch.groupValues[1].trim()))
                continue
            }
            val inlineMatch = INLINE_KEY_RE.find(line) ?: continue
            val keyPath = parseKeySegments(inlineMatch.groupValues[1])
            result.add((section + keyPath).joinToString("."))
        }
        return result
    }

    private fun parseKeySegments(raw: String): List<String> =
        raw.split('.').map { it.trim().removeSurrounding("\"") }

    // ── Writing (Json5 → Config, then TomlWriter) ─────────────────────────────

    private fun json5ToConfig(
        obj: Json5Object,
        sectionPath: List<String>,
        inlinePaths: Set<String>,
        inlineConfigs: MutableSet<Config>
    ): Config {
        val config = TomlFormat.instance().createConfig { LinkedHashMap() }
        for ((key, value) in obj.entrySet()) {
            val fullPath = (sectionPath + key).joinToString(".")
            val configValue: Any = when {
                value.isJson5Object -> {
                    val sub = json5ToConfig(value.asJson5Object, sectionPath + key, inlinePaths, inlineConfigs)
                    if (fullPath in inlinePaths) inlineConfigs.add(sub)
                    sub
                }
                value.isJson5Array  -> json5ArrayToList(value.asJson5Array, sectionPath + key, inlinePaths, inlineConfigs)
                value.isJson5Primitive -> json5PrimitiveToAny(value.asJson5Primitive)
                else -> ""
            }
            config.set<Any>(listOf(key), configValue)
        }
        return config
    }

    private fun json5ArrayToList(
        arr: Json5Array,
        sectionPath: List<String>,
        inlinePaths: Set<String>,
        inlineConfigs: MutableSet<Config>
    ): List<Any?> = (0 until arr.size()).map { i ->
        val elem = arr.get(i)
        when {
            elem.isJson5Object    -> json5ToConfig(elem.asJson5Object, sectionPath, inlinePaths, inlineConfigs)
            elem.isJson5Array     -> json5ArrayToList(elem.asJson5Array, sectionPath, inlinePaths, inlineConfigs)
            elem.isJson5Primitive -> json5PrimitiveToAny(elem.asJson5Primitive)
            else -> null
        }
    }

    private fun json5PrimitiveToAny(p: Json5Primitive): Any = when {
        p.isBoolean -> p.asBoolean
        p.isNumber  -> p.asString.toLongOrNull() ?: p.asString.toDoubleOrNull() ?: p.asString
        else        -> p.asString
    }
}
