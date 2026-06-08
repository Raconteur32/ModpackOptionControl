package fr.raconteur.moc.content

import de.marhali.json5.Json5
import de.marhali.json5.Json5Array
import de.marhali.json5.Json5Element
import de.marhali.json5.Json5Null
import de.marhali.json5.Json5Object
import de.marhali.json5.Json5Primitive

val json5Reader: Json5 = Json5.builder {
    it.quoteSingle().quoteless().parseComments().allowNaN().allowInfinity().build()
}

val json5Writer: Json5 = Json5.builder { it.prettyPrinting().build() }

fun unwrapJson5Element(obj: Any?): Any? = when (obj) {
    is Json5Null      -> null
    is Json5Primitive -> when {
        obj.isBoolean -> obj.asBoolean
        obj.isString  -> obj.asString
        obj.isNumber  -> obj.asNumber
        else          -> obj.asString
    }
    else -> obj
}

fun anyToJson5Element(value: Any?): Json5Element = when (value) {
    null            -> Json5Primitive.fromNull()
    is Json5Element -> value
    is Boolean      -> Json5Primitive.fromBoolean(value)
    is Number       -> Json5Primitive.fromNumber(value)
    is String       -> Json5Primitive.fromString(value)
    is Map<*, *>    -> Json5Object().also { obj ->
        value.forEach { (k, v) -> obj.add(k.toString(), anyToJson5Element(v)) }
    }
    is List<*>      -> Json5Array().also { arr ->
        value.forEach { v -> arr.add(anyToJson5Element(v)) }
    }
    else            -> Json5Primitive.fromString(value.toString())
}
