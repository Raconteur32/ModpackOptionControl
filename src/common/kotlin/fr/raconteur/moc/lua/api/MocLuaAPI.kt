package fr.raconteur.moc.lua.api

import org.luaj.vm2.LuaTable

object MocLuaAPI {

    fun buildTable(): LuaTable {
        val api = LuaTable()
        api.set("mcinstance", McInstanceCategory.buildTable())
        api.set("diff", DiffCategory.buildTable())
        api.set("utils", UtilsCategory.buildTable())
        return api
    }
}