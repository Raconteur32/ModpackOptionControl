package fr.raconteur.moc.filesystem

import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.test.TestPlatformService
import fr.raconteur.moc.versioning.EntryKind
import fr.raconteur.moc.versioning.Patch
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchMode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class MocFileSystemDefaultModeTest {

    private lateinit var platform: TestPlatformService
    private lateinit var fsDir: Path

    @BeforeEach
    fun setup() {
        platform = TestPlatformService.create()
        platform.installAsPlatformService()
        fsDir = Files.createTempDirectory("moc-default-test-")
    }

    @AfterEach
    fun cleanup() {
        platform.cleanup()
        fsDir.toFile().deleteRecursively()
    }

    private fun jsonPatch(name: String, vararg entries: PatchEntry): Patch {
        val meta = entries.map { it.filePath }.toSet().associateWith { mapOf("content" to "json") }
        return Patch(name, entries.toList(), meta)
    }

    private fun valueEntry(file: String, path: String, value: Any, mode: PatchMode) =
        PatchEntry(file, path, null, value, EntryKind.VALUE, mode)

    private fun fileValueEntry(file: String, value: Map<String, Any>, mode: PatchMode) =
        PatchEntry(file, "$", null, value, EntryKind.VALUE, mode)

    private fun readValue(fs: MocFileSystem, file: String, path: String): String? {
        fs.reload()
        return (fs.files.firstOrNull { it.getFileName() == file }
            ?.getFlatContent()?.get(path) as? Json5Primitive)?.asString
    }

    // ── Key level ─────────────────────────────────────────────────────────────

    @Test
    fun `DEFAULT VALUE applies when key does not exist yet`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        assertEquals("10", readValue(fs, "opts.json", "\$['x']"))
    }

    @Test
    fun `DEFAULT VALUE applies when key exists and still matches ref`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        fs.applyPatch(jsonPatch("p2", valueEntry("opts.json", "\$['x']", 20, PatchMode.DEFAULT)))
        assertEquals("20", readValue(fs, "opts.json", "\$['x']"))
    }

    @Test
    fun `DEFAULT VALUE does not apply when key was manually changed from ref`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        fsDir.resolve("opts.json").toFile().writeText("""{"x": 99}""")
        fs.reload()
        fs.applyPatch(jsonPatch("p2", valueEntry("opts.json", "\$['x']", 20, PatchMode.DEFAULT)))
        assertEquals("99", readValue(fs, "opts.json", "\$['x']"))
    }

    // ── Whole file ────────────────────────────────────────────────────────────

    @Test
    fun `DEFAULT VALUE creates file when absent`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1", fileValueEntry("opts.json", mapOf("x" to 10), PatchMode.DEFAULT)))
        assertTrue(fsDir.resolve("opts.json").toFile().exists(),
            "DEFAULT whole-file value must create the file when it does not exist")
    }

    @Test
    fun `DEFAULT VALUE does not replace file when user modified it`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1", fileValueEntry("opts.json", mapOf("x" to 10), PatchMode.DEFAULT)))
        fsDir.resolve("opts.json").toFile().writeText("""{"x": 99}""")
        fs.reload()
        fs.applyPatch(jsonPatch("p2", fileValueEntry("opts.json", mapOf("x" to 20), PatchMode.DEFAULT)))
        assertEquals("99", readValue(fs, "opts.json", "\$['x']"),
            "DEFAULT whole-file value must not override a user-modified file")
    }
}
