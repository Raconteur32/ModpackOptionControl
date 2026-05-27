package fr.raconteur.moc.versioning

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Path

object PatchList {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private fun path(): Path = PlatformService.INSTANCE.getConfigDir().resolve("moc/patch-list.json")

    fun getAll(): List<String> {
        val file = path().toFile()
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(patchName: String) {
        val names = getAll().toMutableList()
        names.add(patchName)
        val file = path().toFile()
        file.parentFile.mkdirs()
        file.writeText(gson.toJson(names))
    }
}
