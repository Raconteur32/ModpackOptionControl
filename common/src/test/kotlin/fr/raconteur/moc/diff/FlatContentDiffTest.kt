package fr.raconteur.moc.diff

import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.content.FlatContentDiff
import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.MocFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class FlatContentDiffTest {

    private lateinit var refDir: Path
    private lateinit var currDir: Path

    @BeforeEach
    fun setUp() {
        refDir  = Files.createTempDirectory("moc-diff-ref-")
        currDir = Files.createTempDirectory("moc-diff-curr-")
    }

    @AfterEach
    fun tearDown() {
        refDir.toFile().deleteRecursively()
        currDir.toFile().deleteRecursively()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun diff(name: String, refJson: String, currJson: String): FlatContentDiff {
        refDir.resolve(name).toFile().writeText(refJson)
        currDir.resolve(name).toFile().writeText(currJson)
        val refFile  = MocFileSystem(refDir).files.first  { it.getFileName() == name }
        val currFile = MocFileSystem(currDir).files.first { it.getFileName() == name }
        return currFile.diffFrom(refFile)
    }

    private fun changed(diff: FlatContentDiff, path: String): OptionDiff.Changed {
        val entry = diff[path]
        assertNotNull(entry, "No diff entry at $path — keys: ${diff.keys}")
        assertInstanceOf(OptionDiff.Changed::class.java, entry,
            "Expected Changed at $path, got ${entry?.javaClass?.simpleName}")
        return entry as OptionDiff.Changed
    }

    private fun prim(value: Any?, label: String = "value"): Json5Primitive {
        assertInstanceOf(Json5Primitive::class.java, value,
            "Expected Json5Primitive for $label, got ${value?.javaClass?.simpleName}")
        return value as Json5Primitive
    }

    // ── int / double distinction ──────────────────────────────────────────────

    @Test
    fun `int changed to different int is reported as Changed`() {
        val d = changed(diff("a.json", """{"val": 54}""", """{"val": 99}"""), "\$['val']")
        assertEquals("54", prim(d.oldValue, "old").asString)
        assertEquals("99", prim(d.newValue, "new").asString)
    }

    @Test
    fun `int changed to same-magnitude double is reported as Changed`() {
        val d = changed(diff("a.json", """{"val": 54}""", """{"val": 54.0}"""), "\$['val']")
        assertEquals("54",   prim(d.oldValue, "old").asString)
        assertEquals("54.0", prim(d.newValue, "new").asString)
    }

    @Test
    fun `double changed to same-magnitude int is reported as Changed`() {
        val d = changed(diff("a.json", """{"val": 54.0}""", """{"val": 54}"""), "\$['val']")
        assertEquals("54.0", prim(d.oldValue, "old").asString)
        assertEquals("54",   prim(d.newValue, "new").asString)
    }

    @Test
    fun `identical int values produce no diff`() {
        val d = diff("a.json", """{"val": 54}""", """{"val": 54}""")
        assertFalse(d.containsKey("\$['val']"), "Identical int must produce no diff")
    }

    @Test
    fun `identical double values produce no diff`() {
        val d = diff("a.json", """{"val": 3.14}""", """{"val": 3.14}""")
        assertFalse(d.containsKey("\$['val']"), "Identical double must produce no diff")
    }

    // ── BigInteger and BigDecimal precision ───────────────────────────────────

    @Test
    fun `BigInteger change is detected and asString has no decimal point`() {
        val d = changed(diff("a.json",
            """{"val": 10000000000000000001}""",
            """{"val": 10000000000000000002}"""), "\$['val']")
        assertEquals("10000000000000000001", prim(d.oldValue, "old").asString)
        assertEquals("10000000000000000002", prim(d.newValue, "new").asString)
    }

    @Test
    fun `identical BigInteger values produce no diff`() {
        val d = diff("a.json",
            """{"val": 10000000000000000001}""",
            """{"val": 10000000000000000001}""")
        assertFalse(d.containsKey("\$['val']"), "Identical BigInteger must produce no diff")
    }

    @Test
    fun `BigDecimal change is detected and asString preserves full precision`() {
        val d = changed(diff("a.json",
            """{"val": 1.1234567890123456789}""",
            """{"val": 2.1234567890123456789}"""), "\$['val']")
        assertEquals("1.1234567890123456789", prim(d.oldValue, "old").asString)
        assertEquals("2.1234567890123456789", prim(d.newValue, "new").asString)
    }

    @Test
    fun `identical BigDecimal values produce no diff`() {
        val d = diff("a.json",
            """{"val": 1.1234567890123456789}""",
            """{"val": 1.1234567890123456789}""")
        assertFalse(d.containsKey("\$['val']"), "Identical BigDecimal must produce no diff")
    }

    @Test
    fun `int 54 and BigDecimal 54_0000000 are reported as Changed`() {
        val d = changed(diff("a.json", """{"val": 54}""", """{"val": 54.0000000}"""), "\$['val']")
        assertEquals("54",         prim(d.oldValue, "old").asString)
        assertEquals("54.0000000", prim(d.newValue, "new").asString)
    }

    // ── JSON5 special values: NaN, Infinity ───────────────────────────────────

    @Test
    fun `identical NaN values produce no diff`() {
        val d = diff("a.json5", """{"val": NaN}""", """{"val": NaN}""")
        assertFalse(d.containsKey("\$['val']"), "Identical NaN must produce no diff")
    }

    @Test
    fun `NaN changed to Infinity is reported as Changed`() {
        val d = changed(diff("a.json5", """{"val": NaN}""", """{"val": Infinity}"""), "\$['val']")
        assertEquals("NaN",      prim(d.oldValue, "old").asString)
        assertEquals("Infinity", prim(d.newValue, "new").asString)
    }

    @Test
    fun `Infinity changed to negative Infinity is reported as Changed`() {
        val d = changed(diff("a.json5", """{"val": Infinity}""", """{"val": -Infinity}"""), "\$['val']")
        assertEquals("Infinity",  prim(d.oldValue, "old").asString)
        assertEquals("-Infinity", prim(d.newValue, "new").asString)
    }

    @Test
    fun `NaN changed to a regular number is reported as Changed`() {
        val d = changed(diff("a.json5", """{"val": NaN}""", """{"val": 42}"""), "\$['val']")
        assertEquals("NaN", prim(d.oldValue, "old").asString)
        assertEquals("42",  prim(d.newValue, "new").asString)
    }

    // ── JSON5 hex literals ─────────────────────────────────────────────────────

    @Test
    fun `identical hex values produce no diff`() {
        val d = diff("a.json5", """{"val": 0xFF}""", """{"val": 0xFF}""")
        assertFalse(d.containsKey("\$['val']"), "Identical hex must produce no diff")
    }

    @Test
    fun `hex value change is detected and asString preserves hex notation`() {
        val d = changed(diff("a.json5", """{"val": 0xFF}""", """{"val": 0xFE}"""), "\$['val']")
        val oldStr = prim(d.oldValue, "old").asString
        val newStr = prim(d.newValue, "new").asString
        assertTrue(oldStr.equals("0xFF", ignoreCase = true), "Old hex must be 0xFF, got: $oldStr")
        assertTrue(newStr.equals("0xFE", ignoreCase = true), "New hex must be 0xFE, got: $newStr")
    }

    @Test
    fun `hex 0xFF and decimal 255 are reported as Changed (different representations)`() {
        val d = changed(diff("a.json5", """{"val": 0xFF}""", """{"val": 255}"""), "\$['val']")
        val oldStr = prim(d.oldValue, "old").asString
        val newStr = prim(d.newValue, "new").asString
        assertNotEquals(oldStr, newStr,
            "0xFF and 255 must have different asString representations so the diff detects the change")
    }
}
