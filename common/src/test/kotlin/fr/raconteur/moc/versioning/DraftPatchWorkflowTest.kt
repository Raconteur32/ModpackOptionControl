package fr.raconteur.moc.versioning

import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.filesystem.McInstanceRefMocFileSystem
import fr.raconteur.moc.filesystem.MocFileSystem
import fr.raconteur.moc.test.TestPlatformService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Files
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DraftPatchWorkflowTest {

    private val platform   = TestPlatformService.create()
    private val gameDir    get() = platform.tempDir
    private val devRefDir  get() = platform.tempDir.resolve("config/moc/dev/ref")

    @BeforeAll
    fun initSingletons() {
        platform.installAsPlatformService()
        // Trigger lazy init with test dirs already in place
        McInstanceMocFileSystem.files
        McInstanceRefMocFileSystem.files
        DraftPatch.entries
    }

    @BeforeEach
    fun reset() {
        // Remove test game files at game dir root
        gameDir.toFile().listFiles { _, name -> name.endsWith(".json") || name.endsWith(".json5") }
            ?.forEach { it.delete() }
        // Wipe and recreate dev-ref
        devRefDir.toFile().deleteRecursively()
        devRefDir.toFile().mkdirs()
        // Wipe patch storage
        platform.tempDir.resolve("config/moc/patchs").toFile().deleteRecursively()
        platform.tempDir.resolve("config/moc/patch-list.json").toFile().delete()
        // Reset DraftPatch state and reload filesystems
        DraftPatch.clear()
        McInstanceMocFileSystem.reload()
        McInstanceRefMocFileSystem.reload()
    }

    @AfterAll
    fun cleanup() {
        platform.cleanup()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun gameFile(name: String, content: String) =
        gameDir.resolve(name).toFile().writeText(content)

    private fun refFile(name: String, content: String) =
        devRefDir.resolve(name).toFile().writeText(content)

    /** Reloads both filesystems and returns the current diff. */
    private fun reloadAndDiff() = McInstanceMocFileSystem
        .also { it.reload() }
        .diffFrom(McInstanceRefMocFileSystem.also { it.reload() })

    private fun prim(flat: Map<String, Any?>, path: String) =
        flat[path].also { assertNotNull(it, "No value at $path") } as Json5Primitive

    // ── Test 1: numeric type preservation through the full pipeline ───────────

    @Test
    fun `entry values preserve numeric types (int, double, BigInteger) at each step`() {
        gameFile("values.json", """{"intVal": 99, "doubleVal": 2.71, "bigInt": 10000000000000000002}""")
        refFile ("values.json", """{"intVal": 54, "doubleVal": 3.14, "bigInt": 10000000000000000001}""")

        val fileDiff = reloadAndDiff()[Path.of("values.json")]!!.flatContentDiff
        DraftPatch.setValueEntry(fileDiff["\$['intVal']"]    as OptionDiff.Changed, PatchMode.OVERRIDE)
        DraftPatch.setValueEntry(fileDiff["\$['doubleVal']"] as OptionDiff.Changed, PatchMode.OVERRIDE)
        DraftPatch.setValueEntry(fileDiff["\$['bigInt']"]    as OptionDiff.Changed, PatchMode.OVERRIDE)

        // Step 1 — in-memory entries
        fun entry(key: String) = DraftPatch.entries.first { it.optionPath == key }
        assertEquals("99",                   entry("\$['intVal']").toValue.toString(),    "in-memory int")
        assertEquals("2.71",                 entry("\$['doubleVal']").toValue.toString(), "in-memory double")
        assertEquals("10000000000000000002", entry("\$['bigInt']").toValue.toString(),    "in-memory BigInt")

        // Step 2 — Gson round-trip: patch.json → Patch.load (registerPreciseNumberStrategy)
        DraftPatch.finalize("types-patch")
        val loaded = Patch.load("types-patch")
        fun loadedEntry(key: String) = loaded.entries.first { it.optionPath == key }
        assertEquals("99",                   loadedEntry("\$['intVal']").toValue.toString(),    "loaded int")
        assertEquals("2.71",                 loadedEntry("\$['doubleVal']").toValue.toString(), "loaded double")
        assertEquals("10000000000000000002", loadedEntry("\$['bigInt']").toValue.toString(),    "loaded BigInt")

        // Step 3 — dev-ref content after patch was applied
        McInstanceRefMocFileSystem.reload()
        val flat = McInstanceRefMocFileSystem.files
            .first { it.getFileName() == "values.json" }
            .getFlatContent()!!
        assertEquals("99",                   prim(flat, "\$['intVal']").asString,    "dev-ref int")
        assertEquals("2.71",                 prim(flat, "\$['doubleVal']").asString, "dev-ref double")
        assertEquals("10000000000000000002", prim(flat, "\$['bigInt']").asString,    "dev-ref BigInt")
    }

    // ── Test 2: finalize updates dev-ref (value change + file deletion) ───────

    @Test
    fun `finalize updates dev-ref and deleted file disappears regardless of entry mode`() {
        gameFile("config.json", """{"x": 42}""")
        // to-delete.json is absent from game dir → it is "deleted" in the diff
        refFile("config.json",    """{"x": 1}""")
        refFile("to-delete.json", """{"y": 2}""")

        val diff = reloadAndDiff()
        val configDiff   = diff[Path.of("config.json")]!!.flatContentDiff
        val deletedEntry = diff[Path.of("to-delete.json")]!!.flatContentDiff[""] as OptionDiff.Deleted

        DraftPatch.setValueEntry  (configDiff["\$['x']"] as OptionDiff.Changed, PatchMode.OVERRIDE)
        DraftPatch.setDeletionEntry(deletedEntry, PatchMode.OVERRIDE)

        assertEquals(2, DraftPatch.entries.size)
        assertEquals("42",              DraftPatch.entries.first { it.optionPath == "\$['x']" }.toValue.toString())
        assertEquals(EntryKind.DELETION, DraftPatch.entries.first { it.optionPath == "" }.kind)

        DraftPatch.finalize("update-patch")

        assertEquals(listOf("update-patch"), McInstanceRefMocFileSystem.appliedPatches,
            "dev-ref must record the applied patch name")

        // dev-ref: x must be 42
        McInstanceRefMocFileSystem.reload()
        val flat = McInstanceRefMocFileSystem.files
            .first { it.getFileName() == "config.json" }
            .getFlatContent()!!
        assertEquals("42", prim(flat, "\$['x']").asString, "dev-ref x must be 42 after patch")

        // dev-ref: to-delete.json must be absent
        assertFalse(
            McInstanceRefMocFileSystem.files.any { it.getFileName() == "to-delete.json" },
            "to-delete.json must be absent from dev-ref after finalize"
        )

        assertTrue(PatchList.contains("update-patch"), "update-patch must be registered in PatchList")
    }

    // ── Test 3: apply to normal FS — DELETION only with OVERRIDE ─────────────

    @Test
    fun `applying patch to normal filesystem only deletes files whose entry mode is OVERRIDE`() {
        // ref has two files absent from game dir → two deletion entries with different modes
        refFile("default-gone.json",  """{"a": 1}""")
        refFile("override-gone.json", """{"b": 1}""")

        val diff = reloadAndDiff()
        DraftPatch.setDeletionEntry(
            diff[Path.of("default-gone.json")]!!.flatContentDiff[""]  as OptionDiff.Deleted,
            PatchMode.DEFAULT
        )
        DraftPatch.setDeletionEntry(
            diff[Path.of("override-gone.json")]!!.flatContentDiff[""] as OptionDiff.Deleted,
            PatchMode.OVERRIDE
        )
        DraftPatch.finalize("deletion-modes-patch")

        assertEquals(listOf("deletion-modes-patch"), McInstanceRefMocFileSystem.appliedPatches,
            "dev-ref must record the applied patch name")

        // Both files are gone from dev-ref (forceDelete=true in finalize)
        McInstanceRefMocFileSystem.reload()
        assertFalse(McInstanceRefMocFileSystem.files.any { it.getFileName() == "default-gone.json" })
        assertFalse(McInstanceRefMocFileSystem.files.any { it.getFileName() == "override-gone.json" })

        // Apply the patch to a fresh filesystem that still has both files
        val targetDir = Files.createTempDirectory("moc-target-")
        try {
            targetDir.resolve("default-gone.json").toFile().writeText("""{"a": 1}""")
            targetDir.resolve("override-gone.json").toFile().writeText("""{"b": 1}""")

            val targetFs = MocFileSystem(targetDir)
            targetFs.applyPatch(Patch.load("deletion-modes-patch"))

            assertEquals(listOf("deletion-modes-patch"), targetFs.appliedPatches,
                "target filesystem must record the applied patch name")

            assertTrue(
                targetDir.resolve("default-gone.json").toFile().exists(),
                "DEFAULT deletion must not apply with forceDelete=false — file must survive"
            )
            assertFalse(
                targetDir.resolve("override-gone.json").toFile().exists(),
                "OVERRIDE deletion must always apply — file must be deleted"
            )
        } finally {
            targetDir.toFile().deleteRecursively()
        }
    }
}
