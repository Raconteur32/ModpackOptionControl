package fr.raconteur.moc.content

import de.marhali.json5.Json5
import de.marhali.json5.Json5Element
import de.marhali.json5.Json5Object
import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.filesystem.MocFile
import java.io.StringReader
import java.util.Properties

object PropertiesContentType : ContentType() {
    override val id = "properties"

    private val json5Writer = Json5.builder { it.build() }

    // Matches the first unescaped =, : or whitespace in a trimmed line.
    // (?:[^\\=: \t]|\\.)* consumes regular chars and escape sequences (\X) before reaching the separator.
    private val FIRST_UNESCAPED_SEP = Regex("""^(?:[^\\=: \t]|\\.)*([=: \t])""")

    override fun hasPreferredExtension(filename: String): Boolean =
        filename.endsWith(".properties")

    override fun hasValidContent(file: MocFile): Boolean {
        val text = file.getStringContent() ?: return false
        if (text.isBlank()) return false
        return try { readOrdered(text); true } catch (_: Exception) { false }
    }

    override fun getContent(file: MocFile): Json5Element? {
        val text = file.getStringContent() ?: return null
        return try {
            Json5Object().also { obj ->
                for ((key, value) in readOrdered(text)) obj.add(key, Json5Primitive.fromString(value))
            }
        } catch (_: Exception) { null }
    }

    override fun setContent(file: MocFile, content: Json5Element) {
        val separator = file.metadata["separator"] ?: "="
        val keyOrder  = file.metadata["key_order"]
            ?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

        if (!content.isJson5Object) return
        val obj = content.asJson5Object

        val contentKeys = obj.keySet()
        val baseOrder = keyOrder.filter { it in contentKeys } +
                        contentKeys.filter { it !in keyOrder }
        val versionOnTop = file.metadata["version_always_on_top"] == "true"
        val orderedKeys = if (versionOnTop && "version" in contentKeys)
            listOf("version") + baseOrder.filter { it != "version" }
        else
            baseOrder

        val lines = orderedKeys.map { key ->
            val value = obj.get(key)
            val valueStr = when {
                value.isJson5Object || value.isJson5Array -> json5Writer.serialize(value)
                value.isJson5Primitive                    -> value.asJson5Primitive.asString
                else                                      -> value.toString()
            }
            "$key$separator$valueStr"
        }
        file.setStringContent(lines.joinToString(System.lineSeparator()))
    }

    override fun getSpecificMetadata(file: MocFile): Map<String, String> {
        val text = file.getStringContent() ?: return emptyMap()
        if (text.isBlank()) return emptyMap()
        val keys = readOrdered(text).keys.toList()
        return buildMap {
            put("separator", detectSeparator(text))
            put("version_always_on_top", "true")
            if (keys.isNotEmpty()) put("key_order", keys.joinToString(","))
        }
    }

    private fun readOrdered(text: String): Map<String, String> {
        val props = OrderedProperties()
        props.load(StringReader(text))
        return props.stringPropertyNames().associateWith { props.getProperty(it) }
    }

    private fun detectSeparator(text: String): String {
        for (line in text.lines()) {
            val trimmed = line.trimStart()
            if (trimmed.isBlank() || trimmed.startsWith('#') || trimmed.startsWith('!')) continue
            val match = FIRST_UNESCAPED_SEP.find(trimmed) ?: continue
            val sep = match.groupValues[1][0]
            return if (sep == ' ' || sep == '\t') " " else sep.toString()
        }
        return "="
    }

    private class OrderedProperties : Properties() {
        private val orderedKeys = LinkedHashSet<Any>()

        override fun put(key: Any, value: Any): Any? {
            orderedKeys.add(key)
            return super.put(key, value)
        }

        override fun stringPropertyNames(): Set<String> =
            orderedKeys.filterIsInstance<String>().toCollection(LinkedHashSet())
    }
}
