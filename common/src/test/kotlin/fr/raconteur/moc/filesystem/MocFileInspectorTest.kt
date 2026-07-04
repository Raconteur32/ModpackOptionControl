package fr.raconteur.moc.filesystem

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MocFileInspectorTest {

    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("moc-inspector-")
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun write(name: String, bytes: ByteArray): Path =
        tempDir.resolve(name).also { Files.write(it, bytes) }

    private fun writeText(name: String, text: String): Path =
        write(name, text.toByteArray(Charsets.UTF_8))

    // ── isBinary ─────────────────────────────────────────────────────────────

    @Test
    fun `isBinary returns false for a plain UTF-8 text file`() {
        val path = writeText("plain.txt", "hello world\n")
        assertFalse(MocFileInspector.isBinary(path))
    }

    @Test
    fun `isBinary returns true for a file containing a null byte`() {
        val path = write("binary.dat", byteArrayOf(0x48, 0x65, 0x00, 0x6C, 0x6F))
        assertTrue(MocFileInspector.isBinary(path))
    }

    @Test
    fun `isBinary returns false for UTF-16BE content without BOM`() {
        val path = write("utf16be.txt", "hello world".toByteArray(Charsets.UTF_16BE))
        assertFalse(MocFileInspector.isBinary(path))
    }

    @Test
    fun `isBinary returns false for UTF-16LE content without BOM`() {
        val path = write("utf16le.txt", "hello world".toByteArray(Charsets.UTF_16LE))
        assertFalse(MocFileInspector.isBinary(path))
    }

    @Test
    fun `isBinary returns false for an empty file`() {
        val path = write("empty.txt", ByteArray(0))
        assertFalse(MocFileInspector.isBinary(path))
    }

    // ── detectEncoding ────────────────────────────────────────────────────────

    @Test
    fun `detectEncoding returns UTF-8 for a plain text file with non-ASCII content and no BOM`() {
        // Pure ASCII content is detected as "US-ASCII" by UniversalDetector; use non-ASCII to get UTF-8.
        val path = writeText("plain.txt", "héllo wörld\n")
        assertEquals("UTF-8", MocFileInspector.detectEncoding(path), ignoreCase = true)
    }

    @Test
    fun `detectEncoding returns UTF-8 for a file with UTF-8 BOM`() {
        // UTF-8 BOM: EF BB BF
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val path = write("utf8bom.txt", bom + "hello".toByteArray(Charsets.UTF_8))
        assertEquals("UTF-8", MocFileInspector.detectEncoding(path), ignoreCase = true)
    }

    @Test
    fun `detectEncoding returns UTF-16BE for a file with UTF-16BE BOM`() {
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val text = "hi".toByteArray(Charsets.UTF_16BE)
        val path = write("utf16be.txt", bom + text)
        assertEquals("UTF-16BE", MocFileInspector.detectEncoding(path), ignoreCase = true)
    }

    @Test
    fun `detectEncoding returns UTF-16LE for a file with UTF-16LE BOM`() {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val text = "hi".toByteArray(Charsets.UTF_16LE)
        val path = write("utf16le.txt", bom + text)
        assertEquals("UTF-16LE", MocFileInspector.detectEncoding(path), ignoreCase = true)
    }

    @Test
    fun `detectEncoding returns UTF-16BE for a file without BOM via heuristic`() {
        val text = "hello world".toByteArray(Charsets.UTF_16BE)
        val path = write("utf16be-nobom.txt", text)
        assertEquals("UTF-16BE", MocFileInspector.detectEncoding(path), ignoreCase = true)
    }

    @Test
    fun `detectEncoding returns UTF-16LE for a file without BOM via heuristic`() {
        val text = "hello world".toByteArray(Charsets.UTF_16LE)
        val path = write("utf16le-nobom.txt", text)
        assertEquals("UTF-16LE", MocFileInspector.detectEncoding(path), ignoreCase = true)
    }

    @Test
    fun `detectEncoding throws IllegalArgumentException for a binary file`() {
        val path = write("binary.dat", byteArrayOf(0x48, 0x65, 0x00, 0x6C, 0x6F))
        assertThrows(IllegalArgumentException::class.java) {
            MocFileInspector.detectEncoding(path)
        }
    }

    // ── inferContentType (via MocFileSystem) ──────────────────────────────────

    @Test
    fun `inferContentType returns PropertiesContentType for options_txt (filename override)`() {
        writeText("options.txt", "key=value\n")
        val file = MocFileSystem(tempDir).files.first { it.getFileName() == "options.txt" }
        assertEquals("properties", file.contentType.id,
            "options.txt must always be treated as properties regardless of extension")
    }

    @Test
    fun `inferContentType selects JsonContentType for a dot-json file`() {
        writeText("settings.json", """{"x": 1}""")
        val file = MocFileSystem(tempDir).files.first { it.getFileName() == "settings.json" }
        assertEquals("json", file.contentType.id)
    }

    @Test
    fun `inferContentType selects TomlContentType for a dot-toml file`() {
        writeText("config.toml", "key = \"value\"\n")
        val file = MocFileSystem(tempDir).files.first { it.getFileName() == "config.toml" }
        assertEquals("toml", file.contentType.id)
    }

    @Test
    fun `inferContentType falls back to TextContentType for an unknown extension`() {
        // PropertiesContentType.hasValidContent returns false for blank text (early-exit guard),
        // so bestScore stays 0 and MocFileInspector falls back to TextContentType.
        writeText("readme.xyz", "   ")
        val file = MocFileSystem(tempDir).files.first { it.getFileName() == "readme.xyz" }
        assertEquals("text", file.contentType.id)
    }

    // ── private helper to compare encoding names case-insensitively ───────────

    private fun assertEquals(expected: String, actual: String, ignoreCase: Boolean) {
        assertTrue(expected.equals(actual, ignoreCase = ignoreCase),
            "Expected encoding '$expected' but got '$actual'")
    }
}
