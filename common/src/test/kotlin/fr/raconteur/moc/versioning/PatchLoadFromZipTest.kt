package fr.raconteur.moc.versioning

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PatchLoadFromZipTest {

    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("moc-zip-test-")
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun createZip(name: String, vararg entries: Pair<String, String>): Path {
        val zipPath = tempDir.resolve(name)
        ZipOutputStream(zipPath.toFile().outputStream()).use { zos ->
            for ((entryName, content) in entries) {
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return zipPath
    }

    private val samplePatchJson = """
        [{"file_path":"config.json","option_path":"${'$'}['level']","from_value":1,"to_value":2,"kind":"VALUE","mode":"OVERRIDE"}]
    """.trimIndent()

    private val sampleMetaJson = """{"config.json": {"content": "json"}}"""

    // ── entries ───────────────────────────────────────────────────────────────

    @Test
    fun `loadFromZip reads PatchEntry list from patch_json`() {
        val zip = createZip("my-patch.zip",
            "patch.json"   to samplePatchJson,
            "mocmeta.json" to sampleMetaJson)
        val patch = Patch.loadFromZip(zip)
        assertEquals(1, patch.entries.size)
        val entry = patch.entries.first()
        assertEquals("config.json",   entry.filePath)
        assertEquals("\$['level']",   entry.optionPath)
        assertEquals(EntryKind.VALUE,    entry.kind)
        assertEquals(PatchMode.OVERRIDE, entry.mode)
    }

    @Test
    fun `loadFromZip returns empty entries if patch_json is absent from the zip`() {
        val zip = createZip("my-patch.zip", "mocmeta.json" to sampleMetaJson)
        val patch = Patch.loadFromZip(zip)
        assertTrue(patch.entries.isEmpty(), "Missing patch.json must result in empty entries")
    }

    @Test
    fun `loadFromZip returns empty entries if patch_json is malformed`() {
        val zip = createZip("my-patch.zip",
            "patch.json"   to "not valid json !!!",
            "mocmeta.json" to sampleMetaJson)
        val patch = Patch.loadFromZip(zip)
        assertTrue(patch.entries.isEmpty(), "Malformed patch.json must result in empty entries")
    }

    // ── metadata ──────────────────────────────────────────────────────────────

    @Test
    fun `loadFromZip reads metadata from mocmeta_json`() {
        val zip = createZip("my-patch.zip",
            "patch.json"   to samplePatchJson,
            "mocmeta.json" to sampleMetaJson)
        val patch = Patch.loadFromZip(zip)
        assertEquals(mapOf("content" to "json"), patch.metadata["config.json"])
    }

    @Test
    fun `loadFromZip returns empty metadata if mocmeta_json is absent`() {
        val zip = createZip("my-patch.zip", "patch.json" to samplePatchJson)
        val patch = Patch.loadFromZip(zip)
        assertTrue(patch.metadata.isEmpty(), "Missing mocmeta.json must result in empty metadata")
    }

    // ── name ──────────────────────────────────────────────────────────────────

    @Test
    fun `name defaults to zip filename without the dot-zip suffix`() {
        val zip = createZip("my-patch.zip", "patch.json" to samplePatchJson)
        val patch = Patch.loadFromZip(zip)
        assertEquals("my-patch", patch.name)
    }

    @Test
    fun `name can be overridden explicitly`() {
        val zip = createZip("file.zip", "patch.json" to samplePatchJson)
        val patch = Patch.loadFromZip(zip, "custom-name")
        assertEquals("custom-name", patch.name)
    }
}
