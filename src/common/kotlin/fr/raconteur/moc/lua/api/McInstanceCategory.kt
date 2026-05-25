package fr.raconteur.moc.lua.api

import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ZeroArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import java.nio.file.Path

object McInstanceCategory {

    fun buildTable(): LuaTable {
        val t = LuaTable()

        t.set("has_file", object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue =
                valueOf(McInstanceMocFileSystem.hasFile(Path.of(path.checkjstring())))
        })

        t.set("get_files", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                val result = LuaTable()
                McInstanceMocFileSystem.files.forEachIndexed { i, file ->
                    result.set(i + 1, valueOf(file.relativePath.toString()))
                }
                return result
            }
        })

        t.set("get_file", object : OneArgFunction() {
            override fun call(path: LuaValue): LuaValue {
                val relativePath = Path.of(path.checkjstring())
                val file = McInstanceMocFileSystem.files.find { it.relativePath == relativePath }
                    ?: throw LuaError("File not found: $relativePath")
                return CoerceJavaToLua.coerce(MocFileAPIWrapper(file))
            }
        })

        return t
    }
}