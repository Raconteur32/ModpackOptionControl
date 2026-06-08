package fr.raconteur.moc.content

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import de.marhali.json5.Json5Array
import de.marhali.json5.Json5Element
import de.marhali.json5.Json5Object
import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.filesystem.MocFile

object TomlContentType : ContentType() {
    override val id = "toml"

    private val mapper = TomlMapper()
    private val mapType = object : TypeReference<Map<String, Any>>() {}

    override fun hasPreferredExtension(filename: String) = filename.endsWith(".toml")

    override fun hasValidContent(file: MocFile): Boolean {
        val text = file.getStringContent() ?: return false
        if (text.isBlank()) return false
        return try { parse(text); true } catch (_: Exception) { false }
    }

    override fun getContent(file: MocFile): Json5Element? {
        val text = file.getStringContent() ?: return null
        return try { mapToJson5(parse(text)) } catch (_: Exception) { null }
    }

    override fun setContent(file: MocFile, content: Json5Element) {
        require(content.isJson5Object) { "TOML root must be an object" }
        file.setStringContent(mapper.writeValueAsString(json5ToMap(content.asJson5Object)))
    }

    private fun parse(text: String): Map<String, Any> = mapper.readValue(text, mapType)

    private fun mapToJson5(map: Map<*, *>): Json5Object =
        Json5Object().also { obj ->
            for ((key, value) in map) obj.add(key.toString(), anyToJson5(value))
        }

    private fun anyToJson5(value: Any?): Json5Element = when (value) {
        is Boolean   -> Json5Primitive.fromBoolean(value)
        is Number    -> Json5Primitive.fromNumber(value)
        is String    -> Json5Primitive.fromString(value)
        is Map<*, *> -> mapToJson5(value)
        is List<*>   -> Json5Array().also { arr -> value.forEach { arr.add(anyToJson5(it)) } }
        else         -> Json5Primitive.fromString(value?.toString() ?: "")
    }

    private fun json5ToMap(obj: Json5Object): LinkedHashMap<String, Any?> =
        LinkedHashMap<String, Any?>().also { map ->
            for ((key, value) in obj.entrySet()) map[key] = json5ToAny(value)
        }

    private fun json5ToAny(element: Json5Element): Any? = when {
        element.isJson5Object  -> json5ToMap(element.asJson5Object)
        element.isJson5Array   -> element.asJson5Array.let { arr ->
            (0 until arr.size()).map { json5ToAny(arr.get(it)) }
        }
        element.isJson5Primitive -> element.asJson5Primitive.let { p ->
            when {
                p.isBoolean -> p.asBoolean
                p.isNumber  -> p.asString.toLongOrNull() ?: p.asString.toDoubleOrNull() ?: p.asString
                else        -> p.asString
            }
        }
        else -> null
    }
}
