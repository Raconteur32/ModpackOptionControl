package fr.raconteur.moc.filesystem

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.InvalidJsonException
import com.jayway.jsonpath.JsonPathException
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.json.AbstractJsonProvider
import com.jayway.jsonpath.spi.mapper.MappingException
import com.jayway.jsonpath.spi.mapper.MappingProvider
import de.marhali.json5.Json5Array
import de.marhali.json5.Json5Element
import de.marhali.json5.Json5Object
import fr.raconteur.moc.content.anyToJson5Element
import fr.raconteur.moc.content.unwrapJson5Element
import fr.raconteur.moc.content.json5Reader
import fr.raconteur.moc.content.json5Writer
import java.io.InputStream
import java.nio.charset.Charset

class Json5JsonProvider : AbstractJsonProvider() {

    override fun parse(json: String): Any? = try {
        json5Reader.parse(json)
    } catch (e: Exception) {
        throw InvalidJsonException(e)
    }

    override fun parse(jsonStream: InputStream, charset: String): Any? =
        parse(jsonStream.bufferedReader(Charset.forName(charset)).readText())

    override fun toJson(obj: Any?): String = when (obj) {
        null            -> "null"
        is Json5Element -> json5Writer.serialize(obj)
        else            -> obj.toString()
    }

    override fun createArray(): Any = Json5Array()
    override fun createMap(): Any = Json5Object()

    override fun isArray(obj: Any?) = obj is Json5Array
    override fun isMap(obj: Any?) = obj is Json5Object

    override fun getArrayIndex(obj: Any?, idx: Int): Any? = unwrap((obj as Json5Array)[idx])

    override fun setArrayIndex(array: Any?, index: Int, newValue: Any?) {
        val arr = array as Json5Array
        val el = anyToJson5Element(newValue)
        if (index == arr.size()) arr.add(el) else arr.set(index, el)
    }

    override fun getMapValue(obj: Any?, key: String): Any? {
        val o = obj as Json5Object
        return if (o.has(key)) unwrap(o.get(key)) else UNDEFINED
    }

    override fun setProperty(obj: Any?, key: Any?, value: Any?) {
        if (isMap(obj)) {
            (obj as Json5Object).add(key.toString(), anyToJson5Element(value))
        } else {
            val arr = obj as Json5Array
            val index = when {
                key == null -> arr.size()
                key is Int  -> key
                else        -> key.toString().toInt()
            }
            if (index == arr.size()) arr.add(anyToJson5Element(value))
            else arr.set(index, anyToJson5Element(value))
        }
    }

    override fun removeProperty(obj: Any?, key: Any?) {
        if (isMap(obj)) {
            (obj as Json5Object).remove(key.toString())
        } else {
            val arr = obj as Json5Array
            val index = if (key is Int) key else key.toString().toInt()
            arr.remove(index)
        }
    }

    override fun getPropertyKeys(obj: Any?): Collection<String> = (obj as Json5Object).keySet()

    override fun length(obj: Any?): Int = when {
        isArray(obj)        -> (obj as Json5Array).size()
        isMap(obj)          -> (obj as Json5Object).size()
        obj is Json5Primitive -> obj.asString.length
        else -> throw JsonPathException("length operation cannot be applied to $obj")
    }

    override fun toIterable(obj: Any?): Iterable<Any?> = obj as Json5Array

    override fun unwrap(obj: Any?): Any? = unwrapJson5Element(obj)
}

class Json5MappingProvider : MappingProvider {
    override fun <T> map(source: Any?, targetType: Class<T>, configuration: Configuration): T {
        if (source == null) @Suppress("UNCHECKED_CAST") return null as T
        if (targetType.isInstance(source)) return targetType.cast(source)
        throw MappingException("Cannot map ${source::class.java} to $targetType")
    }

    override fun <T> map(source: Any?, targetType: TypeRef<T>, configuration: Configuration): T {
        throw MappingException("TypeRef mapping not supported by Json5MappingProvider")
    }
}
