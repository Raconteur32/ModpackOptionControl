package fr.raconteur.moc.content_type_read_write

import de.marhali.json5.Json5Array
import de.marhali.json5.Json5Object
import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.content.FlatContent
import fr.raconteur.moc.filesystem.MocFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class JsonContentTypeTest {

    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("moc-content-type-")
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun copyResource(resource: String, targetName: String) {
        val target = tempDir.resolve(targetName)
        val stream = javaClass.classLoader.getResourceAsStream(resource)
            ?: error("Test resource not found: $resource")
        stream.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
    }

    /** Loads a resource file, asserts it is parsed as JSON, and returns its FlatContent. */
    private fun flatContent(resource: String, name: String): FlatContent {
        copyResource(resource, name)
        val fs = MocFileSystem(tempDir)
        val file = fs.files.first { it.getFileName() == name }
        assertEquals("json", file.contentType.id,
            "$name should be recognised as JSON — if 'text', the parser rejected the content")
        return file.getFlatContent() ?: fail("getFlatContent() returned null for $name")
    }

    /** Returns the Json5Primitive at [path], failing with a clear message if absent or wrong type. */
    private fun prim(flat: FlatContent, path: String): Json5Primitive {
        val v = flat[path]
        assertNotNull(v, "No value at path $path — available keys: ${flat.keys}")
        assertInstanceOf(Json5Primitive::class.java, v,
            "Expected Json5Primitive at $path, got ${v?.javaClass?.simpleName}")
        return v as Json5Primitive
    }

    // ── Standard scalar leaf values (values.json) ─────────────────────────────

    @Test
    fun `integer value is a number primitive with int string representation`() {
        val flat = flatContent("content/values.json", "values.json")
        val p = prim(flat, "\$['intVal']")
        assertTrue(p.isNumber)
        assertEquals("54", p.asString, "int 54 must render as '54', not '54.0'")
        assertEquals(54, p.asInt)
    }

    @Test
    fun `double value is a number primitive with decimal string representation`() {
        val flat = flatContent("content/values.json", "values.json")
        val p = prim(flat, "\$['doubleVal']")
        assertTrue(p.isNumber)
        assertEquals("3.14", p.asString)
        assertEquals(3.14, p.asDouble, 1e-10)
    }

    @Test
    fun `boolean true is a boolean primitive`() {
        val flat = flatContent("content/values.json", "values.json")
        val p = prim(flat, "\$['boolTrue']")
        assertTrue(p.isBoolean)
        assertTrue(p.asBoolean)
    }

    @Test
    fun `boolean false is a boolean primitive`() {
        val flat = flatContent("content/values.json", "values.json")
        val p = prim(flat, "\$['boolFalse']")
        assertTrue(p.isBoolean)
        assertFalse(p.asBoolean)
    }

    @Test
    fun `string value is a string primitive`() {
        val flat = flatContent("content/values.json", "values.json")
        val p = prim(flat, "\$['strVal']")
        assertTrue(p.isString)
        assertFalse(p.isNumber)
        assertEquals("hello", p.asString)
    }

    // ── Node values (objects and arrays) in FlatContent ───────────────────────

    @Test
    fun `root dollar sign is a Json5Object containing all top-level keys`() {
        val flat = flatContent("content/values.json", "values.json")
        val root = flat["$"]
        assertInstanceOf(Json5Object::class.java, root, "Root '\$' must be a Json5Object")
        root as Json5Object
        assertTrue(root.has("intVal"))
        assertTrue(root.has("nested"))
        assertTrue(root.has("arr"))
    }

    @Test
    fun `nested object node is present as a Json5Object in FlatContent`() {
        val flat = flatContent("content/values.json", "values.json")
        val node = flat["\$['nested']"]
        assertNotNull(node, "Path \$['nested'] must be present")
        assertInstanceOf(Json5Object::class.java, node,
            "Nested object must be a Json5Object, got ${node?.javaClass?.simpleName}")
    }

    @Test
    fun `nested object node exposes its direct children`() {
        val flat = flatContent("content/values.json", "values.json")
        val nested = flat["\$['nested']"] as Json5Object
        assertTrue(nested.has("child"), "nested must expose 'child'")
        assertEquals(42, nested.get("child").asInt)
        assertTrue(nested.has("grandchild"), "nested must expose 'grandchild'")
    }

    @Test
    fun `deeply nested grandchild object is also a Json5Object in FlatContent`() {
        val flat = flatContent("content/values.json", "values.json")
        val node = flat["\$['nested']['grandchild']"]
        assertInstanceOf(Json5Object::class.java, node,
            "Grandchild object must be a Json5Object")
        assertEquals("deep", (node as Json5Object).get("leaf").asString)
    }

    @Test
    fun `deeply nested leaf is accessible by its full bracket-notation path`() {
        val flat = flatContent("content/values.json", "values.json")
        val p = prim(flat, "\$['nested']['grandchild']['leaf']")
        assertEquals("deep", p.asString)
    }

    @Test
    fun `array node is a Json5Array in FlatContent with correct size`() {
        val flat = flatContent("content/values.json", "values.json")
        val node = flat["\$['arr']"]
        assertNotNull(node, "Path \$['arr'] must be present")
        assertInstanceOf(Json5Array::class.java, node,
            "Array must be a Json5Array, got ${node?.javaClass?.simpleName}")
        assertEquals(3, (node as Json5Array).size())
    }

    @Test
    fun `array elements are accessible by index paths`() {
        val flat = flatContent("content/values.json", "values.json")
        assertEquals("1", prim(flat, "\$['arr'][0]").asString)
        assertEquals("2", prim(flat, "\$['arr'][1]").asString)
        assertEquals("3", prim(flat, "\$['arr'][2]").asString)
    }

    // ── Numeric type distinction (precision.json) ─────────────────────────────

    @Test
    fun `int 54 and double 54_0 produce different asString (diff relies on this)`() {
        val flat = flatContent("content/precision.json", "precision.json")
        val intStr    = prim(flat, "\$['int54']").asString
        val doubleStr = prim(flat, "\$['double54']").asString
        assertNotEquals(intStr, doubleStr,
            "int 54 and double 54.0 must not be equal as strings, or the diff cannot detect the change")
    }

    @Test
    fun `int 54 asString is '54' without decimal point`() {
        val flat = flatContent("content/precision.json", "precision.json")
        assertEquals("54", prim(flat, "\$['int54']").asString)
    }

    @Test
    fun `double 54_0 asString preserves the decimal point`() {
        val flat = flatContent("content/precision.json", "precision.json")
        assertEquals("54.0", prim(flat, "\$['double54']").asString)
    }

    @Test
    fun `BigInteger preserves full precision in asString without rounding`() {
        val flat = flatContent("content/precision.json", "precision.json")
        val p = prim(flat, "\$['bigInt']")
        assertTrue(p.isNumber)
        assertEquals("10000000000000000001", p.asString,
            "Large integer must not be rounded to a double representation")
    }

    @Test
    fun `high-precision decimal preserves full string representation`() {
        val flat = flatContent("content/precision.json", "precision.json")
        val p = prim(flat, "\$['bigDecimal']")
        assertTrue(p.isNumber)
        assertEquals("1.1234567890123456789", p.asString,
            "High-precision decimal must not be truncated to double precision (~15 digits)")
    }

    // ── JSON5 special values (json5.json5) ────────────────────────────────────

    @Test
    fun `NaN is parsed as a number primitive by json5-java`() {
        val flat = flatContent("content/json5.json5", "json5.json5")
        val p = prim(flat, "\$['nanVal']")
        assertTrue(p.isNumber, "json5-java stores NaN as a number primitive (RadixNumber)")
        assertEquals("NaN", p.asString)
        assertTrue(p.asDouble.isNaN())
    }

    @Test
    fun `Infinity is parsed as a number primitive`() {
        val flat = flatContent("content/json5.json5", "json5.json5")
        val p = prim(flat, "\$['infVal']")
        assertTrue(p.isNumber)
        assertEquals("Infinity", p.asString)
        assertTrue(p.asDouble.isInfinite() && p.asDouble > 0)
    }

    @Test
    fun `negative Infinity is parsed as a number primitive`() {
        val flat = flatContent("content/json5.json5", "json5.json5")
        val p = prim(flat, "\$['negInfVal']")
        assertTrue(p.isNumber)
        assertEquals("-Infinity", p.asString)
        assertTrue(p.asDouble.isInfinite() && p.asDouble < 0)
    }

    @Test
    fun `hex literal 0xFF is a number primitive with hex string representation`() {
        val flat = flatContent("content/json5.json5", "json5.json5")
        val p = prim(flat, "\$['hexVal']")
        assertTrue(p.isNumber, "json5-java parses 0xFF as a proper hex number primitive")
        assertTrue(
            p.asString.equals("0xFF", ignoreCase = true),
            "Expected hex notation '0xFF', got: '${p.asString}'"
        )
        assertEquals(255, p.asInt)
    }

    @Test
    fun `multiline string with backslash continuation contains both parts`() {
        val flat = flatContent("content/json5.json5", "json5.json5")
        val p = prim(flat, "\$['multilineVal']")
        assertTrue(p.isString)
        val value = p.asString
        assertTrue(value.contains("first") && value.contains("second"),
            "Parsed multiline string must contain 'first' and 'second', got: '$value'")
    }

    // ── Write round-trip ──────────────────────────────────────────────────────

    @Test
    fun `setContent writes valid JSON that getFlatContent can read back`() {
        copyResource("content/values.json", "rw.json")
        val fs = MocFileSystem(tempDir)
        val file = fs.files.first { it.getFileName() == "rw.json" }

        val newRoot = Json5Object().apply {
            add("written", Json5Primitive.fromNumber(99))
            add("flag", Json5Primitive.fromBoolean(true))
        }
        file.setContent(newRoot)

        // Re-scan the filesystem so MocFile is re-read from disk
        val fs2 = MocFileSystem(tempDir)
        val file2 = fs2.files.first { it.getFileName() == "rw.json" }
        val flat = file2.getFlatContent() ?: fail("getFlatContent() returned null after setContent")

        assertEquals("99",   prim(flat, "\$['written']").asString)
        assertEquals("true", prim(flat, "\$['flag']").asString)
        assertFalse(flat.containsKey("\$['intVal']"), "Original keys must be gone after full replacement")
    }
}
