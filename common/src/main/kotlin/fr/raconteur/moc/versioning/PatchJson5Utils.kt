package fr.raconteur.moc.versioning

import de.marhali.json5.Json5Array
import de.marhali.json5.Json5Element
import de.marhali.json5.Json5Object
import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.content.anyToJson5Element
import fr.raconteur.moc.content.json5Reader
import fr.raconteur.moc.content.json5Writer
import fr.raconteur.moc.content.unwrapJson5Element

fun List<PatchEntry>.toJson5String(): String =
    json5Writer.serialize(Json5Array().also { arr -> forEach { arr.add(it.toJson5Object()) } })

fun parsePatchEntries(text: String): List<PatchEntry> {
    val root = try { json5Reader.parse(text) } catch (_: Exception) { return emptyList() }
    if (!root.isJson5Array) return emptyList()
    return root.asJson5Array.mapNotNull { el ->
        if (!el.isJson5Object) return@mapNotNull null
        val obj = el.asJson5Object
        try {
            PatchEntry(
                filePath   = obj.get("file_path").asJson5Primitive.asString,
                optionPath = obj.get("option_path").asJson5Primitive.asString,
                fromValue  = unwrapJson5Element(obj.get("from_value")),
                toValue    = unwrapJson5Element(obj.get("to_value")),
                kind       = EntryKind.valueOf(obj.get("kind").asJson5Primitive.asString),
                mode       = PatchMode.valueOf(obj.get("mode").asJson5Primitive.asString)
            )
        } catch (_: Exception) { null }
    }
}

internal fun json5ToNative(value: Any?): Any? = when (value) {
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

private fun PatchEntry.toJson5Object(): Json5Object = Json5Object().apply {
    add("file_path",   Json5Primitive.fromString(filePath))
    add("option_path", Json5Primitive.fromString(optionPath))
    add("from_value",  anyToJson5Element(fromValue))
    add("to_value",    anyToJson5Element(toValue))
    add("kind",        Json5Primitive.fromString(kind.name))
    add("mode",        Json5Primitive.fromString(mode.name))
}
