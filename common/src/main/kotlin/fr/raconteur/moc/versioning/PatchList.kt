package fr.raconteur.moc.versioning

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Path

object PatchList {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /** Name of the directory under `config/moc/` holding one folder per patch. Single source of truth. */
    const val PATCHES_DIR_NAME = "patches"

    private fun path(): Path = PlatformService.INSTANCE.getConfigDir().resolve("moc/patch-list.json")
    private fun deletedPath(): Path = PlatformService.INSTANCE.getConfigDir().resolve("moc/deleted-patch-list.json")
    fun patchesRoot(): Path = PlatformService.INSTANCE.getConfigDir().resolve("moc/$PATCHES_DIR_NAME")
    internal fun patchDir(name: String) = patchesRoot().resolve(name).toFile()

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

    fun getAllDeleted(): List<String> {
        val file = deletedPath().toFile()
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun contains(patchName: String): Boolean = getAll().contains(patchName)

    fun add(patchName: String) {
        val names = getAll().toMutableList()
        if (names.contains(patchName)) return
        names.add(patchName)
        writeAll(names)
        removeFromDeleted(patchName)
    }

    fun setAll(names: List<String>) = writeAll(names)

    fun delete(patchName: String) {
        val names = getAll().toMutableList()
        names.remove(patchName)
        writeAll(names)
        addToDeleted(patchName)
        patchDir(patchName).deleteRecursively()
    }

    fun runStartupCleanup() {
        getAllDeleted().forEach { patchDir(it).deleteRecursively() }
    }

    fun addToDeleted(patchName: String) {
        val names = getAllDeleted().toMutableList()
        if (!names.contains(patchName)) {
            names.add(patchName)
            val file = deletedPath().toFile()
            file.parentFile.mkdirs()
            file.writeText(gson.toJson(names))
        }
    }

    fun deleteFolder(patchName: String) {
        patchDir(patchName).deleteRecursively()
    }

    private fun writeAll(names: List<String>) {
        val file = path().toFile()
        file.parentFile.mkdirs()
        file.writeText(gson.toJson(names))
    }

    private fun removeFromDeleted(patchName: String) {
        val names = getAllDeleted().toMutableList()
        if (names.remove(patchName)) {
            deletedPath().toFile().writeText(gson.toJson(names))
        }
    }
}
