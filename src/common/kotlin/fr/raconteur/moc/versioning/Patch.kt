package fr.raconteur.moc.versioning

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fr.raconteur.moc.platform.PlatformService

class Patch(val name: String, val entries: List<PatchEntry>) {
    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()

        fun load(patchName: String): Patch {
            val path = PlatformService.INSTANCE.getConfigDir().resolve("moc/patchs/$patchName.json")
            val type = object : TypeToken<List<PatchEntry>>() {}.type
            val entries: List<PatchEntry> = try {
                gson.fromJson(path.toFile().readText(), type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            return Patch(patchName, entries)
        }
    }
}
