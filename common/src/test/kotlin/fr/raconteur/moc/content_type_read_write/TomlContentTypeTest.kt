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

class TomlContentTypeTest {

    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("moc-toml-")
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
    fun `toml file is recognised as toml content type`() {
        val file = writeFile("config.toml", "key = \"value\"\n")
        assertEquals("toml", file.contentType.id)
    }

    // ── Scalar reading ────────────────────────────────────────────────────────

    @Test
    fun `string value is read as a string primitive`() {
        val file = writeFile("config.toml", "title = \"hello\"\n")
        val o = obj(file)
        val p = o.get("title")
        assertTrue(p.isJson5Primitive && p.asJson5Primitive.isString)
        assertEquals("hello", p.asString)
    }

    @Test
    fun `integer value is read as a number primitive`() {
        val file = writeFile("config.toml", "count = 42\n")
        val p = obj(file).get("count").asJson5Primitive
        assertTrue(p.isNumber)
        assertEquals(42, p.asInt)
    }

    @Test
    fun `float value is read as a number primitive`() {
        val file = writeFile("config.toml", "ratio = 3.14\n")
        val p = obj(file).get("ratio").asJson5Primitive
        assertTrue(p.isNumber)
        assertEquals(3.14, p.asDouble, 1e-10)
    }

    @Test
    fun `boolean true is read as a boolean primitive`() {
        val file = writeFile("config.toml", "enabled = true\n")
        val p = obj(file).get("enabled").asJson5Primitive
        assertTrue(p.isBoolean)
        assertTrue(p.asBoolean)
    }

    @Test
    fun `boolean false is read as a boolean primitive`() {
        val file = writeFile("config.toml", "enabled = false\n")
        val p = obj(file).get("enabled").asJson5Primitive
        assertTrue(p.isBoolean)
        assertFalse(p.asBoolean)
    }

    // ── Nested tables ─────────────────────────────────────────────────────────

    @Test
    fun `table section is read as a nested Json5Object`() {
        val file = writeFile("config.toml", "[database]\nhost = \"localhost\"\nport = 5432\n")
        val db = obj(file).get("database")
        assertInstanceOf(Json5Object::class.java, db)
        db as Json5Object
        assertEquals("localhost", db.get("host").asString)
        assertEquals(5432, db.get("port").asJson5Primitive.asInt)
    }

    @Test
    fun `multiple table sections produce separate nested objects`() {
        val file = writeFile("config.toml",
            "[server]\nhost = \"0.0.0.0\"\n\n[database]\nhost = \"localhost\"\n")
        val root = obj(file)
        assertEquals("0.0.0.0",   (root.get("server")   as Json5Object).get("host").asString)
        assertEquals("localhost", (root.get("database") as Json5Object).get("host").asString)
    }

    // ── Arrays ────────────────────────────────────────────────────────────────

    @Test
    fun `inline array of strings is read as a Json5Array`() {
        val file = writeFile("config.toml", "tags = [\"a\", \"b\", \"c\"]\n")
        val arr = obj(file).get("tags")
        assertInstanceOf(Json5Array::class.java, arr)
        arr as Json5Array
        assertEquals(3, arr.size())
        assertEquals("a", arr.get(0).asString)
        assertEquals("c", arr.get(2).asString)
    }

    @Test
    fun `inline array of integers is read as a Json5Array of number primitives`() {
        val file = writeFile("config.toml", "ports = [8080, 8443, 9000]\n")
        val arr = obj(file).get("ports") as Json5Array
        assertEquals(3, arr.size())
        assertEquals(8080, arr.get(0).asJson5Primitive.asInt)
    }

    // ── getFlatContent integration ────────────────────────────────────────────

    @Test
    fun `getFlatContent exposes nested table keys by bracket-notation path`() {
        val file = writeFile("config.toml", "[database]\nhost = \"localhost\"\n")
        val flat = file.getFlatContent() ?: fail("getFlatContent() returned null")
        assertNotNull(flat["\$['database']['host']"],
            "Path \$['database']['host'] must exist — available: ${flat.keys}")
        assertEquals("localhost", flat["\$['database']['host']"]?.let {
            (it as de.marhali.json5.Json5Primitive).asString
        })
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Test
    fun `setContent writes scalar values as valid TOML`() {
        val file = writeFile("config.toml", "key = \"old\"\n")
        file.setContent(Json5Object().apply {
            add("title",   Json5Primitive.fromString("test"))
            add("count",   Json5Primitive.fromNumber(7))
            add("enabled", Json5Primitive.fromBoolean(true))
        })
        val written = tempDir.resolve("config.toml").toFile().readText()
        assertTrue("test" in written,  "Expected value 'test' in:\n$written")
        assertTrue("7" in written,     "Expected integer 7 in:\n$written")
        assertTrue("true" in written,  "Expected boolean true in:\n$written")
    }

    @Test
    fun `setContent writes nested object as dotted keys or table section`() {
        val file = writeFile("config.toml", "key = \"old\"\n")
        file.setContent(Json5Object().apply {
            add("database", Json5Object().apply {
                add("host", Json5Primitive.fromString("localhost"))
                add("port", Json5Primitive.fromNumber(5432))
            })
        })
        val written = tempDir.resolve("config.toml").toFile().readText()
        assertTrue("database" in written,   "Expected 'database' key in:\n$written")
        assertTrue("localhost" in written,  "Expected value 'localhost' in:\n$written")
    }

    @Test
    fun `setContent writes array values`() {
        val file = writeFile("config.toml", "key = \"old\"\n")
        file.setContent(Json5Object().apply {
            add("tags", Json5Array().apply {
                add(Json5Primitive.fromString("a"))
                add(Json5Primitive.fromString("b"))
            })
        })
        val written = tempDir.resolve("config.toml").toFile().readText()
        assertTrue("tags" in written, "Expected 'tags' key in:\n$written")
        assertTrue("a" in written,    "Expected array element 'a' in:\n$written")
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `round-trip preserves scalar values`() {
        tempDir.resolve("config.toml").toFile().writeText(
            "title = \"hello\"\ncount = 42\nenabled = true\nratio = 3.14\n"
        )
        val fs1 = MocFileSystem(tempDir)
        val file1 = fs1.files.first { it.getFileName() == "config.toml" }
        file1.setContent(file1.getContent()!!)

        val fs2 = MocFileSystem(tempDir)
        val o = obj(fs2.files.first { it.getFileName() == "config.toml" })
        assertEquals("hello", o.get("title").asString)
        assertEquals(42,      o.get("count").asJson5Primitive.asInt)
        assertTrue(           o.get("enabled").asJson5Primitive.asBoolean)
        assertEquals(3.14,    o.get("ratio").asJson5Primitive.asDouble, 1e-10)
    }

    @Test
    fun `round-trip preserves inline table values`() {
        tempDir.resolve("config.toml").toFile().writeText("point = {x = 1, y = 2}\n")
        val fs1 = MocFileSystem(tempDir)
        val file1 = fs1.files.first { it.getFileName() == "config.toml" }
        file1.setContent(file1.getContent()!!)

        val fs2 = MocFileSystem(tempDir)
        val point = obj(fs2.files.first { it.getFileName() == "config.toml" })
            .get("point") as Json5Object
        assertEquals(1, point.get("x").asJson5Primitive.asInt)
        assertEquals(2, point.get("y").asJson5Primitive.asInt)
    }

    @Test
    fun `round-trip preserves nested table`() {
        tempDir.resolve("config.toml").toFile().writeText(
            "[database]\nhost = \"localhost\"\nport = 5432\n"
        )
        val fs1 = MocFileSystem(tempDir)
        val file1 = fs1.files.first { it.getFileName() == "config.toml" }
        file1.setContent(file1.getContent()!!)

        val fs2 = MocFileSystem(tempDir)
        val db = obj(fs2.files.first { it.getFileName() == "config.toml" })
            .get("database") as Json5Object
        assertEquals("localhost", db.get("host").asString)
        assertEquals(5432,        db.get("port").asJson5Primitive.asInt)
    }

    // ── Inline / standard format preservation ────────────────────────────────

    @Test
    fun `inline table path is stored in metadata`() {
        val file = writeFile("config.toml", "point = {x = 1, y = 2}\n")
        assertEquals("point", file.metadata["inline_tables"])
    }

    @Test
    fun `standard table has no inline_tables metadata`() {
        val file = writeFile("config.toml", "[database]\nhost = \"localhost\"\n")
        assertNull(file.metadata["inline_tables"])
    }

    @Test
    fun `round-trip preserves inline table format`() {
        tempDir.resolve("config.toml").toFile().writeText("point = {x = 1, y = 2}\n")
        val fs1 = MocFileSystem(tempDir)
        val file1 = fs1.files.first { it.getFileName() == "config.toml" }
        file1.setContent(file1.getContent()!!)

        val written = tempDir.resolve("config.toml").toFile().readText()
        assertTrue("{" in written,       "Expected inline table notation in:\n$written")
        assertFalse("[point]" in written, "Expected no section header for inline table in:\n$written")
    }

    @Test
    fun `round-trip preserves standard table format`() {
        tempDir.resolve("config.toml").toFile().writeText("[database]\nhost = \"localhost\"\n")
        val fs1 = MocFileSystem(tempDir)
        val file1 = fs1.files.first { it.getFileName() == "config.toml" }
        file1.setContent(file1.getContent()!!)

        val written = tempDir.resolve("config.toml").toFile().readText()
        assertTrue("[database]" in written, "Expected section header in:\n$written")
        assertFalse("{" in written,         "Expected no inline notation for standard table in:\n$written")
    }

    @Test
    fun `round-trip preserves mixed inline and standard tables`() {
        tempDir.resolve("config.toml").toFile().writeText(
            "point = {x = 1, y = 2}\n\n[server]\nhost = \"localhost\"\n"
        )
        val fs1 = MocFileSystem(tempDir)
        val file1 = fs1.files.first { it.getFileName() == "config.toml" }
        file1.setContent(file1.getContent()!!)

        val written = tempDir.resolve("config.toml").toFile().readText()
        assertTrue("{" in written,        "Expected inline table in:\n$written")
        assertTrue("[server]" in written,  "Expected section header in:\n$written")
    }
}
