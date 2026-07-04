package fr.raconteur.moc.content

import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.filesystem.MocFile
import fr.raconteur.moc.filesystem.MocFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TextContentTypeTest {

    private lateinit var tempDir: Path
    private lateinit var fs: MocFileSystem

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("moc-text-")
        fs = MocFileSystem(tempDir)
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    // Use ensureWritable to bypass content-type auto-detection and force TextContentType.
    // Auto-detection cannot be relied upon here: PropertiesContentType.hasValidContent returns
    // true for virtually any non-blank text (Properties.load is very permissive), giving it
    // a score of 1 and outcompeting the TextContentType fallback.
    private fun textFile(name: String, content: String): MocFile {
        tempDir.resolve(name).toFile().writeText(content)
        return MocFile.ensureWritable(fs, Path.of(name), contentTypeId = "text")
    }

    // ── ContentType contract ──────────────────────────────────────────────────

    @Test
    fun `hasPreferredExtension returns true for any filename`() {
        assertTrue(TextContentType.hasPreferredExtension("anything.xyz"))
        assertTrue(TextContentType.hasPreferredExtension("file.json"))
        assertTrue(TextContentType.hasPreferredExtension("noextension"))
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Test
    fun `getContent wraps file text as a Json5Primitive string`() {
        val file = textFile("readme.txt", "hello world")
        val content = file.getContent()
        assertNotNull(content)
        assertInstanceOf(Json5Primitive::class.java, content,
            "Expected Json5Primitive, got ${content?.javaClass?.simpleName}")
        assertEquals("hello world", content!!.asString)
    }

    @Test
    fun `getContent preserves multi-line text including newlines`() {
        val text = "line one\nline two\n"
        val file = textFile("notes.txt", text)
        assertEquals(text, file.getContent()?.asString)
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Test
    fun `setContent writes the primitive asString to the file`() {
        val file = textFile("readme.txt", "old content")
        file.setContent(Json5Primitive.fromString("new content"))
        assertEquals("new content", tempDir.resolve("readme.txt").toFile().readText())
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `round-trip read-then-write preserves content exactly`() {
        val original = "alpha\nbeta\ngamma\n"
        val file = textFile("log.txt", original)
        file.setContent(file.getContent()!!)
        assertEquals(original, tempDir.resolve("log.txt").toFile().readText())
    }
}
