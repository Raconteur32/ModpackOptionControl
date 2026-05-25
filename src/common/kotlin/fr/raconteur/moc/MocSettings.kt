package fr.raconteur.moc

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object MocSettings {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val defaultIgnoredPaths = listOf("mods", "resourcepacks", "logs", "config/moc.json", "config/moc")

    private fun settingsPath(): Path =
        PlatformService.INSTANCE.getConfigDir().resolve("moc.json")

    private val data: JsonObject by lazy {
        val path = settingsPath()
        if (path.exists()) {
            gson.fromJson(path.readText(), JsonObject::class.java)
        } else {
            JsonObject().also { json ->
                json.add("ignored_paths", gson.toJsonTree(defaultIgnoredPaths))
                path.createParentDirectories()
                path.writeText(gson.toJson(json))
            }
        }
    }

    val ignoredPaths: List<Path> by lazy {
        data.getAsJsonArray("ignored_paths")
            ?.map { Path.of(it.asString) }
            ?: defaultIgnoredPaths.map { Path.of(it) }
    }
}