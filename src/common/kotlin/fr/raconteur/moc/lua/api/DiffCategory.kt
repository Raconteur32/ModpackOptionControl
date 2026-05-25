package fr.raconteur.moc.lua.api

import fr.raconteur.moc.content.FlatContentDiff
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.ZeroArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua

object DiffCategory {

    fun buildTable(): LuaTable {
        val t = LuaTable()

        t.set("get_new_flat_diff", object : ZeroArgFunction() {
            override fun call(): LuaValue =
                CoerceJavaToLua.coerce(FlatContentDiffAPIWrapper(FlatContentDiff()))
        })

        return t
    }
}