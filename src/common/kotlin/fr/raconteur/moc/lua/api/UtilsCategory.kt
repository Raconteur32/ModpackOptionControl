package fr.raconteur.moc.lua.api

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ZeroArgFunction

object UtilsCategory {

    fun buildTable(): LuaTable {
        val t = LuaTable()

        t.set("get_empty_array", object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaTable().also { it.setmetatable(ARRAY_METATABLE) }
        })

        t.set("get_empty_map", object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaTable().also { it.setmetatable(MAP_METATABLE) }
        })

        t.set("is_integer", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue = LuaValue.valueOf(arg.isint())
        })

        return t
    }
}
