package fr.raconteur.moc.web

import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.filesystem.MocFileSystem
import fr.raconteur.moc.test.TestPlatformService
import fr.raconteur.moc.versioning.EntryKind
import fr.raconteur.moc.versioning.Patch
import fr.raconteur.moc.versioning.PatchList
import fr.raconteur.moc.versioning.PatchMode
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Files
import java.nio.file.Path

// Moved from common's test suite with the extraction of the authoring engine
// (see openspec/changes/extract-authoring-from-common) — it exercises the
// web module's embedded copy of DraftPatch / McInstanceRefMocFileSystem.
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
        platform.tempDir.resolve("config/moc/patches").toFile().deleteRecursively()
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
        (flat[path] ?: error("No value at $path")) as Json5Primitive

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

        assertEquals(listOf("update-patch"), McInstanceRefMocFileSystem.appliedPatches.map { it.patch },
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

    // ── Test 3: finalize clears the draft state ───────────────────────────────

    @Test
    fun `finalize clears all draft entries`() {
        gameFile("opts.json", """{"x": 2}""")
        refFile("opts.json",  """{"x": 1}""")

        val diff = reloadAndDiff()
        DraftPatch.setValueEntry(
            diff[Path.of("opts.json")]!!.flatContentDiff["\$['x']"] as OptionDiff.Changed,
            PatchMode.OVERRIDE
        )
        assertTrue(DraftPatch.entries.isNotEmpty(), "precondition: draft must have entries before finalize")
        DraftPatch.finalize("cleanup-test-patch")
        assertTrue(DraftPatch.entries.isEmpty(), "finalize must clear all draft entries")
    }

    // ── Test 4: apply to normal FS — DELETION only with OVERRIDE ─────────────

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

        assertEquals(listOf("deletion-modes-patch"), McInstanceRefMocFileSystem.appliedPatches.map { it.patch },
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

            assertEquals(listOf("deletion-modes-patch"), targetFs.appliedPatches.map { it.patch },
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

    // ── Test: round-trip of a patch capturing an emptied pinned-json file ─────

    @Test
    fun `patch capturing an emptied file records effective type text and applies as a genuinely empty file`() {
        // Live file pinned json (loaded while valid), then emptied; ref keeps the old content.
        gameFile("emptied.json", """{"a": 1, "b": {"c": "x"}}""")
        McInstanceMocFileSystem.reload()
        gameFile("emptied.json", "")
        refFile("emptied.json", """{"a": 1, "b": {"c": "x"}}""")

        val diff = reloadAndDiff()
        val flat = diff[Path.of("emptied.json")]!!.flatContentDiff
        val rootChange = flat["$"] as? OptionDiff.Changed
            ?: error("emptied file must diff at the root, got: ${flat.keys}")
        assertEquals("", (rootChange.newValue as Json5Primitive).asString)

        DraftPatch.setValueEntry(rootChange, PatchMode.OVERRIDE)
        DraftPatch.finalize("empty-patch")

        // The patch declares the effective capture type, not the stale json pin.
        val meta = Patch.load("empty-patch").metadata["emptied.json"]
        assertEquals("text", meta?.get("content"), "mocmeta must record the effective content type")

        // Round-trip: applying on a fresh instance produces a genuinely empty file,
        // not the two-character JSON literal "".
        val targetDir = Files.createTempDirectory("moc-target-")
        try {
            targetDir.resolve("emptied.json").toFile().writeText("""{"a": 1, "b": {"c": "x"}}""")
            val targetFs = MocFileSystem(targetDir)
            targetFs.applyPatch(Patch.load("empty-patch"), forceOverride = true)

            assertEquals("", targetDir.resolve("emptied.json").toFile().readText(),
                "applied file must be genuinely empty")

            // No residual diff: replay the patch as the reference and compare.
            val replayDir = Files.createTempDirectory("moc-replay-")
            try {
                val replayFs = MocFileSystem(replayDir)
                replayFs.applyPatch(Patch.load("empty-patch"), forceOverride = true)
                val residual = MocFileSystem(targetDir).diffFrom(replayFs)
                assertTrue(residual.isEmpty(), "no residual diff after applying the patch, got: ${residual.keys}")
            } finally {
                replayDir.toFile().deleteRecursively()
            }
        } finally {
            targetDir.toFile().deleteRecursively()
        }
    }
}
