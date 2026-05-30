package fr.raconteur.moc.versioning

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fr.raconteur.moc.platform.PlatformService

class Patch(
    val name: String,
    val entries: List<PatchEntry>,
    val metadata: Map<String, Map<String, String>> = emptyMap()
) {
    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().registerSmartAnyDeserializer().create()

        fun load(patchName: String): Patch {
            val dir = PlatformService.INSTANCE.getConfigDir().resolve("moc/patchs/$patchName")
            val entriesType = object : TypeToken<List<PatchEntry>>() {}.type
            val metaType = object : TypeToken<Map<String, Map<String, String>>>() {}.type

            val entries: List<PatchEntry> = try {
                gson.fromJson(dir.resolve("patch.json").toFile().readText(), entriesType) ?: emptyList()
            } catch (_: Exception) { emptyList() }

            val metadata: Map<String, Map<String, String>> = try {
                gson.fromJson(dir.resolve(".mocmeta.json").toFile().readText(), metaType) ?: emptyMap()
            } catch (_: Exception) { emptyMap() }

            return Patch(patchName, entries, metadata)
        }
    }
}
