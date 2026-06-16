package fr.raconteur.moc

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object MocSettings {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val defaultIgnoredPaths = listOf("mods", "resourcepacks", "logs", "config/moc.json", "config/moc", ".mocmetadata.json")

    private fun settingsPath(): Path =
        PlatformService.INSTANCE.getConfigDir().resolve("moc.json")

    private val _data: JsonObject by lazy {
        val path = settingsPath()
        if (path.exists()) {
            gson.fromJson(path.readText(), JsonObject::class.java) ?: JsonObject()
        } else {
            JsonObject().also { json ->
                json.add("ignored_paths", gson.toJsonTree(defaultIgnoredPaths))
                path.createParentDirectories()
                path.writeText(gson.toJson(json))
            }
        }
    }

    // MutableList so that McInstanceMocFileSystem / McInstanceRefMocFileSystem,
    // which capture this reference at construction time, see in-place additions.
    private val _ignoredPaths: MutableList<Path> by lazy {
        _data.getAsJsonArray("ignored_paths")
            ?.mapTo(mutableListOf()) { Path.of(it.asString) }
            ?: defaultIgnoredPaths.mapTo(mutableListOf()) { Path.of(it) }
    }

    val ignoredPaths: List<Path> get() = _ignoredPaths

    fun addIgnoredPath(pathStr: String) {
        val p = Path.of(pathStr)
        if (_ignoredPaths.any { it == p }) return
        _ignoredPaths.add(p)
        val arr = _data.getAsJsonArray("ignored_paths")
            ?: JsonArray().also { _data.add("ignored_paths", it) }
        arr.add(pathStr)
        val sp = settingsPath()
        sp.createParentDirectories()
        sp.writeText(gson.toJson(_data))
    }
}
