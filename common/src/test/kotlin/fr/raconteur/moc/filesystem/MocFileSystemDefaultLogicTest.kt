package fr.raconteur.moc.filesystem

import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.test.TestPlatformService
import fr.raconteur.moc.versioning.EntryKind
import fr.raconteur.moc.versioning.Patch
import fr.raconteur.moc.versioning.PatchEntry
import fr.raconteur.moc.versioning.PatchMode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class MocFileSystemDefaultLogicTest {

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

    // Build a Patch in memory with JSON metadata so applyPatch creates .json files correctly.
    private fun jsonPatch(name: String, vararg entries: PatchEntry): Patch {
        val meta = entries.map { it.filePath }.toSet().associateWith { mapOf("content" to "json") }
        return Patch(name, entries.toList(), meta)
    }

    private fun valueEntry(file: String, path: String, value: Any, mode: PatchMode) =
        PatchEntry(file, path, null, value, EntryKind.VALUE, mode)

    private fun deletionEntry(file: String, mode: PatchMode) =
        PatchEntry(file, "", null, null, EntryKind.DELETION, mode)

    private fun readValue(fs: MocFileSystem, file: String, path: String): String? {
        fs.reload()
        return (fs.files.firstOrNull { it.getFileName() == file }
            ?.getFlatContent()?.get(path) as? Json5Primitive)?.asString
    }

    // ── DEFAULT VALUE — key absent ────────────────────────────────────────────

    @Test
    fun `DEFAULT VALUE applies when key does not exist yet`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        assertEquals("10", readValue(fs, "opts.json", "\$['x']"))
    }

    // ── DEFAULT VALUE — key exists, matches ref (user hasn't diverged) ────────

    @Test
    fun `DEFAULT VALUE applies when key exists and still matches ref`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        // p1 sets x=10 DEFAULT → both main FS and internal ref get x=10
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        // p2 changes default to x=20 → should apply because x=10 == ref x=10
        fs.applyPatch(jsonPatch("p2", valueEntry("opts.json", "\$['x']", 20, PatchMode.DEFAULT)))
        assertEquals("20", readValue(fs, "opts.json", "\$['x']"))
    }

    // ── DEFAULT VALUE — key exists, user diverged from ref ────────────────────

    @Test
    fun `DEFAULT VALUE does not apply when key was manually changed from ref`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        // p1 sets x=10 → main FS and ref both get x=10
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        // user manually sets x=99 (diverges from ref x=10)
        fsDir.resolve("opts.json").toFile().writeText("""{"x": 99}""")
        fs.reload()
        // p2 DEFAULT x=20 → must NOT apply because x=99 ≠ ref x=10
        fs.applyPatch(jsonPatch("p2", valueEntry("opts.json", "\$['x']", 20, PatchMode.DEFAULT)))
        assertEquals("99", readValue(fs, "opts.json", "\$['x']"))
    }

    // ── OVERRIDE VALUE — always applies regardless of ref ─────────────────────

    @Test
    fun `OVERRIDE VALUE applies even when key was manually changed`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        // user diverges to x=99
        fsDir.resolve("opts.json").toFile().writeText("""{"x": 99}""")
        fs.reload()
        // OVERRIDE forces x=20 regardless of ref divergence
        fs.applyPatch(jsonPatch("p2", valueEntry("opts.json", "\$['x']", 20, PatchMode.OVERRIDE)))
        assertEquals("20", readValue(fs, "opts.json", "\$['x']"))
    }

    // ── DEFAULT DELETION — file matches ref (not user-modified) ──────────────

    @Test
    fun `DEFAULT DELETION applies when file is unchanged from ref`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        // p1 creates opts.json in both main FS and ref
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        fs.reload()
        // p2 DEFAULT deletion → file matches ref → should delete
        fs.applyPatch(jsonPatch("p2", deletionEntry("opts.json", PatchMode.DEFAULT)))
        assertFalse(fsDir.resolve("opts.json").toFile().exists())
    }

    // ── DEFAULT DELETION — no ref FS (pre-existing behaviour) ────────────────

    @Test
    fun `DEFAULT DELETION does not apply without a ref FS`() {
        fsDir.resolve("opts.json").toFile().writeText("""{"x": 10}""")
        val fs = MocFileSystem(fsDir)  // hasRef = false (default)
        // DEFAULT deletion → no ref → matchesRef = false → must not apply
        fs.applyPatch(jsonPatch("p1", deletionEntry("opts.json", PatchMode.DEFAULT)))
        assertTrue(fsDir.resolve("opts.json").toFile().exists())
    }
}
