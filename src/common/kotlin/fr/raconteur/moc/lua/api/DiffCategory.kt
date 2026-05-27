package fr.raconteur.moc.lua.api

import fr.raconteur.moc.content.FlatContentDiff
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua

object DiffCategory {

    fun buildTable(): LuaTable {
        val t = LuaTable()

        t.set("get_new_flat_diff", object : OneArgFunction() {
            override fun call(filePath: LuaValue): LuaValue =
                CoerceJavaToLua.coerce(FlatContentDiffAPIWrapper(FlatContentDiff(filePath.checkjstring())))
        })

        return t
    }
}