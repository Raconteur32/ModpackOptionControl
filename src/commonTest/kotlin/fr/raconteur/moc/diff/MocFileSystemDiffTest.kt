package fr.raconteur.moc.diff

import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.FileDiffKind
import fr.raconteur.moc.filesystem.MocFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MocFileSystemDiffTest {

    private lateinit var refDir: Path
    private lateinit var currDir: Path

    @BeforeEach
    fun setUp() {
        refDir  = Files.createTempDirectory("moc-fs-diff-ref-")
        currDir = Files.createTempDirectory("moc-fs-diff-curr-")
    }

    @AfterEach
    fun tearDown() {
        refDir.toFile().deleteRecursively()
        currDir.toFile().deleteRecursively()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun refFile(name: String, content: String)  { refDir.resolve(name).toFile().writeText(content) }
    private fun currFile(name: String, content: String) { currDir.resolve(name).toFile().writeText(content) }

    private fun diff() = MocFileSystem(currDir).diffFrom(MocFileSystem(refDir))

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `file absent in ref and present in current is reported as NEW`() {
        currFile("added.json", """{"x": 1}""")

        val d = diff()

        assertEquals(setOf(Path.of("added.json")), d.getNewPaths())
        assertTrue(d.getDeletedPaths().isEmpty())
        assertTrue(d.getChangedPaths().isEmpty())
    }

    @Test
    fun `file present in ref and absent in current is reported as DELETED with empty-path Deleted entry`() {
        refFile("removed.json", """{"x": 1}""")

        val d = diff()

        assertEquals(setOf(Path.of("removed.json")), d.getDeletedPaths())
        assertTrue(d.getNewPaths().isEmpty())
        assertTrue(d.getChangedPaths().isEmpty())

        val fileDiff = d[Path.of("removed.json")]!!
        assertEquals(FileDiffKind.DELETED, fileDiff.kind)

        val entry = fileDiff.flatContentDiff[""]
        assertNotNull(entry, "FlatContentDiff must contain an entry at path \"\" for a deleted file")
        assertInstanceOf(OptionDiff.Deleted::class.java, entry,
            "Entry at \"\" must be OptionDiff.Deleted, got ${entry?.javaClass?.simpleName}")
    }

    @Test
    fun `file present in both with different content is reported as CHANGED`() {
        refFile("config.json",  """{"level": 1}""")
        currFile("config.json", """{"level": 2}""")

        val d = diff()

        assertEquals(setOf(Path.of("config.json")), d.getChangedPaths())
        assertTrue(d.getNewPaths().isEmpty())
        assertTrue(d.getDeletedPaths().isEmpty())

        val entry = d[Path.of("config.json")]!!.flatContentDiff["\$['level']"]
        assertInstanceOf(OptionDiff.Changed::class.java, entry,
            "Entry at \$['level'] must be OptionDiff.Changed")
    }

    @Test
    fun `file present in both with identical content produces no diff entry`() {
        refFile("config.json",  """{"level": 1}""")
        currFile("config.json", """{"level": 1}""")

        val d = diff()

        assertTrue(d.isEmpty(), "Identical files must not appear in the diff")
    }
}
