package fr.raconteur.moc.lua.api

import com.google.gson.JsonElement
import fr.raconteur.moc.filesystem.MocFile
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

@Suppress("FunctionName")
class MocFileAPIWrapper(private val file: MocFile) {

    fun get_relative_path(): String = file.relativePath.toString()
    fun get_encoding(): String = file.encoding
    fun get_content_type(): String = file.contentType.id
    fun get_raw_content(): String? = file.getStringContent()
    fun get_content(): LuaValue? = file.getContent()?.let { jsonToLua(it) }

    fun get_flat_content(): LuaTable? = file.getFlatContent()?.let { flatContent ->
        val t = LuaTable()
        for ((path, value) in flatContent) {
            t.set(path, if (value is JsonElement) jsonToLua(value) else LuaValue.NIL)
        }
        t
    }
}