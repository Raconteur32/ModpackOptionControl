package fr.raconteur.moc.lua.api

import fr.raconteur.moc.content.FlatContentDiff
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.math.BigDecimal
import java.math.BigInteger

@Suppress("FunctionName")
class FlatContentDiffAPIWrapper(private val flatContentDiff: FlatContentDiff) {

    fun add_new(path: String, newValue: Any?)                     = flatContentDiff.addNew(path, sanitize(newValue))
    fun add_deleted(path: String, oldValue: Any?)                 = flatContentDiff.addDeleted(path, sanitize(oldValue))
    fun add_changed(path: String, oldValue: Any?, newValue: Any?) = flatContentDiff.addChanged(path, sanitize(oldValue), sanitize(newValue))

    fun has_leaf(path: String): Boolean = flatContentDiff.hasLeaf(path)
    fun cut_branch(path: String)        = flatContentDiff.cutBranch(path)
    fun rationalize()                   = flatContentDiff.rationalize()

    private fun sanitize(value: Any?): Any? = when (value) {
        null            -> null
        is Boolean      -> value
        is Int          -> value
        is Double       -> value
        is String       -> value
        is BigInteger   -> value
        is BigDecimal   -> value
        is LuaTable     -> luaTableToJava(value)
        else            -> null
    }

    private fun luaTableToJava(table: LuaTable): Any {
        val length = table.length()
        return if (length > 0) {
            (1..length).map { i -> sanitize(table.get(i)) }
        } else {
            val map = mutableMapOf<String, Any?>()
            var key = LuaValue.NIL
            while (true) {
                val entry = table.next(key)
                key = entry.arg1()
                if (key.isnil()) break
                map[key.tojstring()] = sanitize(entry.arg(2))
            }
            map
        }
    }
}
