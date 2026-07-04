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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class MocFileSystemDeletionTest {

    private lateinit var platform: TestPlatformService
    private lateinit var fsDir: Path

    @BeforeEach
    fun setup() {
        platform = TestPlatformService.create()
        platform.installAsPlatformService()
        fsDir = Files.createTempDirectory("moc-deletion-test-")
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

    private fun fileDeletionEntry(file: String, mode: PatchMode) =
        PatchEntry(file, "", null, null, EntryKind.DELETION, mode)

    private fun keyDeletionEntry(file: String, keyPath: String, mode: PatchMode) =
        PatchEntry(file, keyPath, null, null, EntryKind.DELETION, mode)

    private fun readValue(fs: MocFileSystem, file: String, path: String): String? {
        fs.reload()
        return (fs.files.firstOrNull { it.getFileName() == file }
            ?.getFlatContent()?.get(path) as? Json5Primitive)?.asString
    }

    // ── File deletion — DEFAULT ───────────────────────────────────────────────

    @Test
    fun `DEFAULT file deletion applies when file is unchanged from ref`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        fs.reload()
        fs.applyPatch(jsonPatch("p2", fileDeletionEntry("opts.json", PatchMode.DEFAULT)))
        assertFalse(fsDir.resolve("opts.json").toFile().exists())
    }

    @Test
    fun `DEFAULT file deletion does not apply when file was manually changed from ref`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        fsDir.resolve("opts.json").toFile().writeText("""{"x": 99}""")
        fs.reload()
        fs.applyPatch(jsonPatch("p2", fileDeletionEntry("opts.json", PatchMode.DEFAULT)))
        assertTrue(fsDir.resolve("opts.json").toFile().exists(),
            "DEFAULT file deletion must not apply when file was manually changed from ref")
    }

    @Test
    fun `DEFAULT file deletion does not apply without a ref FS`() {
        fsDir.resolve("opts.json").toFile().writeText("""{"x": 10}""")
        val fs = MocFileSystem(fsDir)
        fs.applyPatch(jsonPatch("p1", fileDeletionEntry("opts.json", PatchMode.DEFAULT)))
        assertTrue(fsDir.resolve("opts.json").toFile().exists())
    }

    // ── File deletion — OVERRIDE ──────────────────────────────────────────────

    @Test
    fun `OVERRIDE file deletion applies even when file was manually changed from ref`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        fsDir.resolve("opts.json").toFile().writeText("""{"x": 99}""")
        fs.reload()
        fs.applyPatch(jsonPatch("p2", fileDeletionEntry("opts.json", PatchMode.OVERRIDE)))
        assertFalse(fsDir.resolve("opts.json").toFile().exists(),
            "OVERRIDE file deletion must apply even when file differs from ref")
    }

    // ── Key deletion — DEFAULT ────────────────────────────────────────────────

    @Test
    fun `DEFAULT key deletion removes key when it matches ref`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        fs.reload()
        fs.applyPatch(jsonPatch("p2", keyDeletionEntry("opts.json", "\$['x']", PatchMode.DEFAULT)))
        assertNull(readValue(fs, "opts.json", "\$['x']"),
            "DEFAULT key deletion must remove the key when it matches ref")
    }

    @Test
    fun `DEFAULT key deletion preserves key when user modified it`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        fsDir.resolve("opts.json").toFile().writeText("""{"x": 99}""")
        fs.reload()
        fs.applyPatch(jsonPatch("p2", keyDeletionEntry("opts.json", "\$['x']", PatchMode.DEFAULT)))
        assertEquals("99", readValue(fs, "opts.json", "\$['x']"),
            "DEFAULT key deletion must not remove a key the user has modified")
    }

    @Test
    fun `DEFAULT key deletion removes key even when other keys in the file were modified`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1",
            valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT),
            valueEntry("opts.json", "\$['y']", 20, PatchMode.DEFAULT)))
        // user changes y but leaves x untouched
        fsDir.resolve("opts.json").toFile().writeText("""{"x": 10, "y": 99}""")
        fs.reload()
        fs.applyPatch(jsonPatch("p2", keyDeletionEntry("opts.json", "\$['x']", PatchMode.DEFAULT)))
        assertNull(readValue(fs, "opts.json", "\$['x']"),
            "DEFAULT key deletion must apply when the target key matches ref, regardless of other keys")
        assertEquals("99", readValue(fs, "opts.json", "\$['y']"),
            "Other user-modified keys must be preserved")
    }

    // ── Key deletion — OVERRIDE ───────────────────────────────────────────────

    @Test
    fun `OVERRIDE key deletion removes key regardless of user modification`() {
        val fs = MocFileSystem(fsDir, hasRef = true)
        fs.applyPatch(jsonPatch("p1", valueEntry("opts.json", "\$['x']", 10, PatchMode.DEFAULT)))
        fsDir.resolve("opts.json").toFile().writeText("""{"x": 99}""")
        fs.reload()
        fs.applyPatch(jsonPatch("p2", keyDeletionEntry("opts.json", "\$['x']", PatchMode.OVERRIDE)))
        assertNull(readValue(fs, "opts.json", "\$['x']"),
            "OVERRIDE key deletion must always remove the key")
    }
}
