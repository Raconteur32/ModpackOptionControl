package fr.raconteur.moc.content_type_read_write

import de.marhali.json5.Json5Array
import de.marhali.json5.Json5Object
import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.filesystem.MocFile
import fr.raconteur.moc.filesystem.MocFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class PropertiesContentTypeTest {

    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("moc-properties-")
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun writeFile(name: String, content: String): MocFile {
        tempDir.resolve(name).toFile().writeText(content)
        return MocFileSystem(tempDir).files.first { it.getFileName() == name }
    }

    private fun obj(file: MocFile): Json5Object {
        val content = file.getContent()
        assertNotNull(content, "getContent() returned null")
        assertInstanceOf(Json5Object::class.java, content,
            "Expected Json5Object, got ${content?.javaClass?.simpleName}")
        return content as Json5Object
    }

    // ── Recognition ──────────────────────────────────────────────────────────

    @Test
    fun `properties file is recognised as properties content type`() {
        val file = writeFile("config.properties", "host=localhost\n")
        assertEquals("properties", file.contentType.id)
    }

    // ── Separator detection ───────────────────────────────────────────────────

    @Test
    fun `equals separator is detected`() {
        val file = writeFile("config.properties", "host=localhost\nport=8080\n")
        assertEquals("=", file.metadata["separator"])
    }

    @Test
    fun `colon separator is detected`() {
        val file = writeFile("config.properties", "host:localhost\nport:8080\n")
        assertEquals(":", file.metadata["separator"])
    }

    @Test
    fun `space separator is detected`() {
        val file = writeFile("config.properties", "host localhost\nport 8080\n")
        assertEquals(" ", file.metadata["separator"])
    }

    @Test
    fun `comment lines are skipped during separator detection`() {
        val file = writeFile("config.properties", "# comment=ignored\nhost=localhost\n")
        assertEquals("=", file.metadata["separator"])
    }

    @Test
    fun `escaped equals in key does not affect separator detection`() {
        // key\=name=value — \= is an escape sequence, the real separator is the second =
        val file = writeFile("config.properties", "key\\=name=value\n")
        assertEquals("=", file.metadata["separator"])
    }

    // ── Content reading ───────────────────────────────────────────────────────

    @Test
    fun `getContent returns flat Json5Object with correct string values`() {
        val file = writeFile("config.properties", "host=localhost\nport=8080\n")
        val o = obj(file)
        assertEquals("localhost", o.get("host").asString)
        assertEquals("8080", o.get("port").asString)
    }

    @Test
    fun `colon-separated file is read correctly`() {
        val file = writeFile("config.properties", "host:localhost\n")
        assertEquals("localhost", obj(file).get("host").asString)
    }

    @Test
    fun `space-separated file is read correctly`() {
        val file = writeFile("config.properties", "host localhost\n")
        assertEquals("localhost", obj(file).get("host").asString)
    }

    @Test
    fun `comment lines are not included in content`() {
        val file = writeFile("config.properties", "# comment\n! also comment\nkey=value\n")
        val o = obj(file)
        assertEquals(1, o.entrySet().size, "Only 'key' should be present, not comment lines")
        assertTrue(o.has("key"))
    }

    @Test
    fun `escaped key is unescaped by java Properties parser`() {
        // java.util.Properties interprets \= as literal = in the key
        val file = writeFile("config.properties", "key\\=name=value\n")
        val o = obj(file)
        assertTrue(o.has("key=name"), "Expected key 'key=name', available: ${o.entrySet().map { it.key }}")
        assertEquals("value", o.get("key=name").asString)
    }

    // ── Content writing ───────────────────────────────────────────────────────

    @Test
    fun `setContent writes key=value pairs with equals separator`() {
        val file = writeFile("config.properties", "host=localhost\n")
        file.setContent(Json5Object().apply {
            add("host", Json5Primitive.fromString("example.com"))
            add("port", Json5Primitive.fromString("9090"))
        })
        val written = tempDir.resolve("config.properties").toFile().readText()
        assertTrue("host=example.com" in written, "Expected 'host=example.com' in:\n$written")
        assertTrue("port=9090" in written, "Expected 'port=9090' in:\n$written")
    }

    @Test
    fun `setContent uses colon separator from metadata`() {
        val file = writeFile("config.properties", "host:localhost\n")
        file.setContent(Json5Object().apply {
            add("host", Json5Primitive.fromString("example.com"))
        })
        val written = tempDir.resolve("config.properties").toFile().readText()
        assertTrue("host:example.com" in written, "Expected 'host:example.com' in:\n$written")
    }

    @Test
    fun `setContent uses space separator from metadata`() {
        val file = writeFile("config.properties", "host localhost\n")
        file.setContent(Json5Object().apply {
            add("host", Json5Primitive.fromString("example.com"))
        })
        val written = tempDir.resolve("config.properties").toFile().readText()
        assertTrue("host example.com" in written, "Expected 'host example.com' in:\n$written")
    }

    @Test
    fun `setContent serializes nested object as json5 string`() {
        val file = writeFile("config.properties", "key=value\n")
        file.setContent(Json5Object().apply {
            add("obj", Json5Object().apply { add("a", Json5Primitive.fromNumber(1)) })
        })
        val written = tempDir.resolve("config.properties").toFile().readText()
        assertTrue(written.startsWith("obj="), "Expected line starting with 'obj=' in:\n$written")
        assertFalse("\n  " in written, "Nested object must not produce indented multi-line output")
    }

    @Test
    fun `setContent serializes array value as json5 string`() {
        val file = writeFile("config.properties", "key=value\n")
        file.setContent(Json5Object().apply {
            add("list", Json5Array().apply {
                add(Json5Primitive.fromString("a"))
                add(Json5Primitive.fromString("b"))
            })
        })
        val written = tempDir.resolve("config.properties").toFile().readText()
        assertTrue(written.startsWith("list="), "Expected line starting with 'list=' in:\n$written")
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `read then write round-trip preserves all values`() {
        tempDir.resolve("config.properties").toFile().writeText("host=localhost\nport=8080\n")
        val fs1 = MocFileSystem(tempDir)
        val file1 = fs1.files.first { it.getFileName() == "config.properties" }
        file1.setContent(file1.getContent()!!)

        val fs2 = MocFileSystem(tempDir)
        val file2 = fs2.files.first { it.getFileName() == "config.properties" }
        val o = obj(file2)
        assertEquals("localhost", o.get("host").asString)
        assertEquals("8080", o.get("port").asString)
    }
}
