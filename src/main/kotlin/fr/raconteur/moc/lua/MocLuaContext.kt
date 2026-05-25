package fr.raconteur.moc.lua

import fr.raconteur.moc.ModpackOptionControl
import fr.raconteur.moc.lua.api.MocLuaAPI
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.ResourceFinder
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.jse.JseBaseLib
import org.luaj.vm2.lib.jse.JseMathLib
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

open class MocLuaContext(moduleName: String) {

    private val scriptsDir: Path = ModpackOptionControl.getInstanceDir().resolve("config/moc/scripts")
    private val modpackScriptsDir: Path = ModpackOptionControl.getInstanceDir().resolve("config/moc/modpack_scripts")

    private val globals: Globals = createSandboxedGlobals()
    private val module: LuaValue = globals["require"].call(moduleName)

    private fun createSandboxedGlobals(): Globals {
        val globals = Globals()
        globals.load(JseBaseLib())
        globals.load(PackageLib())
        globals.load(StringLib())
        globals.load(TableLib())
        globals.load(JseMathLib())
        LuaC.install(globals)

        globals.set("api", MocLuaAPI.buildTable())

        globals.finder = ResourceFinder { filename ->
            val modpackFile = modpackScriptsDir.resolve(filename)
            if (modpackFile.exists()) return@ResourceFinder modpackFile.inputStream()

            val scriptFile = scriptsDir.resolve(filename)
            if (scriptFile.exists()) return@ResourceFinder scriptFile.inputStream()

            null
        }

        return globals
    }

    fun hasFunction(name: String): Boolean = module[name].isfunction()

    protected fun getFunction(name: String): LuaValue? = module[name]

    fun callLuaFunction(name: String, vararg args: LuaValue): LuaValue {
        val fn = module[name]
        if (fn.isnil()) throw LuaError("Function '$name' not found in module")
        return fn.invoke(LuaValue.varargsOf(args)).arg1()
    }
}
