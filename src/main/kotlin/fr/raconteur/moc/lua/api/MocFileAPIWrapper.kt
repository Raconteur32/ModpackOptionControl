package fr.raconteur.moc.lua.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import fr.raconteur.moc.filesystem.MocFile
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import java.math.BigDecimal
import java.math.BigInteger

@Suppress("FunctionName")
class MocFileAPIWrapper(private val file: MocFile) {

    fun get_relative_path(): String = file.relativePath.toString()
    fun get_encoding(): String = file.encoding
    fun get_content_type(): String = file.contentType.getId()
    fun get_raw_content(): String = file.getStringContent()
    fun get_content(): LuaValue = jsonToLua(file.getContent())

    fun get_flat_content(): LuaTable {
        val t = LuaTable()
        for ((path, value) in file.getFlatContent()) {
            t.set(path, if (value is JsonElement) jsonToLua(value) else LuaValue.NIL)
        }
        return t
    }
}

private fun jsonToLua(element: JsonElement): LuaValue = when (element) {
    is JsonNull      -> LuaValue.NIL
    is JsonPrimitive -> when {
        element.isBoolean -> LuaValue.valueOf(element.asBoolean)
        element.isNumber  -> {
            val bd = element.asBigDecimal
            if (bd.stripTrailingZeros().scale() <= 0) {
                val bi = bd.toBigInteger()
                if (bi >= BigInteger.valueOf(Int.MIN_VALUE.toLong()) && bi <= BigInteger.valueOf(Int.MAX_VALUE.toLong()))
                    LuaValue.valueOf(bi.toInt())
                else
                    CoerceJavaToLua.coerce(bi)
            } else {
                val d = bd.toDouble()
                if (BigDecimal.valueOf(d).compareTo(bd) == 0)
                    LuaValue.valueOf(d)
                else
                    CoerceJavaToLua.coerce(bd)
            }
        }
        else              -> LuaValue.valueOf(element.asString)
    }
    is JsonArray  -> LuaTable().also { t ->
        element.forEachIndexed { i, child -> t.set(i + 1, jsonToLua(child)) }
    }
    is JsonObject -> LuaTable().also { t ->
        element.entrySet().forEach { (k, v) -> t.set(k, jsonToLua(v)) }
    }
    else -> LuaValue.NIL
}

internal fun luaToJson(value: LuaValue): JsonElement = when {
    value.isnil()                          -> JsonNull.INSTANCE
    value.isboolean()                      -> JsonPrimitive(value.toboolean())
    value.isuserdata(BigInteger::class.java) -> JsonPrimitive(value.touserdata() as BigInteger)
    value.isuserdata(BigDecimal::class.java) -> JsonPrimitive(value.touserdata() as BigDecimal)
    value.isint()                          -> JsonPrimitive(value.toint())
    value.isnumber()                       -> JsonPrimitive(value.todouble())
    value.isstring()                       -> JsonPrimitive(value.tojstring())
    value.istable()                        -> {
        val table = value.checktable()
        if (table.length() > 0) {
            JsonArray().also { arr ->
                for (i in 1..table.length()) arr.add(luaToJson(table.get(i)))
            }
        } else {
            JsonObject().also { obj ->
                var k = LuaValue.NIL
                while (true) {
                    val entry = table.next(k)
                    k = entry.arg1()
                    if (k.isnil()) break
                    obj.add(k.tojstring(), luaToJson(entry.arg(2)))
                }
            }
        }
    }
    else                                   -> JsonNull.INSTANCE
}