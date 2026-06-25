package fr.raconteur.moc.versioning

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fr.raconteur.moc.platform.PlatformService
import java.util.zip.ZipFile

class Patch(
    val name: String,
    val entries: List<PatchEntry>,
    val metadata: Map<String, Map<String, String>> = emptyMap()
) {
    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()

        fun load(patchName: String): Patch =
            loadFromDir(PlatformService.INSTANCE.getConfigDir().resolve("moc/patchs/$patchName"), patchName)

        fun loadFromZip(zipPath: java.nio.file.Path, name: String = zipPath.fileName.toString().removeSuffix(".zip")): Patch {
            val metaType = object : TypeToken<Map<String, Map<String, String>>>() {}.type
            ZipFile(zipPath.toFile()).use { zip ->
                val entries: List<PatchEntry> = try {
                    val entry = zip.getEntry("patch.json") ?: error("patch.json not found in zip")
                    parsePatchEntries(zip.getInputStream(entry).reader().readText())
                } catch (_: Exception) { emptyList() }
                val metadata: Map<String, Map<String, String>> = try {
                    val entry = zip.getEntry("mocmeta.json") ?: error("mocmeta.json not found in zip")
                    gson.fromJson(zip.getInputStream(entry).reader().readText(), metaType) ?: emptyMap()
                } catch (_: Exception) { emptyMap() }
                return Patch(name, entries, metadata)
            }
        }

        fun loadFromDir(dir: java.nio.file.Path, name: String = dir.fileName.toString()): Patch {
            val metaType = object : TypeToken<Map<String, Map<String, String>>>() {}.type
            val entries: List<PatchEntry> = try {
                parsePatchEntries(dir.resolve("patch.json").toFile().readText())
            } catch (_: Exception) { emptyList() }
            val metadata: Map<String, Map<String, String>> = try {
                gson.fromJson(dir.resolve("mocmeta.json").toFile().readText(), metaType) ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
            return Patch(name, entries, metadata)
        }
    }
}
