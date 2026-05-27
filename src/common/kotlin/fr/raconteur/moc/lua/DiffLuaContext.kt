package fr.raconteur.moc.lua

import fr.raconteur.moc.content.FlatContentDiff
import fr.raconteur.moc.lua.api.FlatContentDiffAPIWrapper
import fr.raconteur.moc.lua.api.MocFileAPIWrapper
import org.luaj.vm2.LuaClosure
import org.luaj.vm2.lib.jse.CoerceJavaToLua

class DiffLuaContext(moduleName: String) : MocLuaContext(moduleName) {

    init {
        if (!hasFunction("diff")) throw RuntimeException("Module '$moduleName' has no diff function")
    }

    fun diff(from: MocFileAPIWrapper, to: MocFileAPIWrapper, filePath: String): FlatContentDiff {
        val flatContentDiff = FlatContentDiff(filePath)
        callLuaFunction(
            "diff",
            CoerceJavaToLua.coerce(from),
            CoerceJavaToLua.coerce(to),
            CoerceJavaToLua.coerce(FlatContentDiffAPIWrapper(flatContentDiff))
        )
        return flatContentDiff
    }

    companion object {
        fun isValid(moduleName: String): Boolean = try {
            val ctx = DiffLuaContext(moduleName)
            val fn = ctx.getFunction("diff")
            fn is LuaClosure && fn.p.numparams == 3
        } catch (_: Exception) {
            false
        }
    }
}