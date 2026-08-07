package fr.raconteur.moc.filesystem

import fr.raconteur.moc.content.JsonContentType
import fr.raconteur.moc.content.TextContentType
import fr.raconteur.moc.content.OptionDiff
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

// Raw-content fallback (fix-emptied-file-diff): an existing file whose content cannot be
// parsed under its pinned content type reads as { "$": <raw string> } via the text
// fallback, without its stored metadata being rewritten.
class MocFileFallbackTest {

    private lateinit var dir: Path

    @BeforeEach
    fun setUp() { dir = Files.createTempDirectory("moc-fallback-") }

    @AfterEach
    fun tearDown() { dir.toFile().deleteRecursively() }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun writeFile(name: String, content: String) { dir.resolve(name).toFile().writeText(content) }

    private fun pinType(name: String, contentType: String) {
        val metas = dir.resolve("mocfsmetas")
        metas.toFile().mkdirs()
        metas.resolve("mocmetadata.json").toFile()
            .writeText("""{ "$name": { "encoding": "UTF-8", "content": "$contentType" } }""")
    }

    private fun storedContentType(name: String): String {
        val text = dir.resolve("mocfsmetas/mocmetadata.json").toFile().readText()
        return Regex(""""$name"\s*:\s*\{[^}]*"content"\s*:\s*"(\w+)"""").find(text)!!.groupValues[1]
    }

    private fun loadFile(name: String) = MocFileSystem(dir).files.single { it.relativePath == Path.of(name) }

    // ── 1.2 fallback matrix ───────────────────────────────────────────────────

    @Test
    fun `unparsable pinned-json content falls back to raw string at root`() {
        val variants = mapOf(
            "empty.json"       to "",
            "whitespace.json"  to "  \n",
            "invalid.json"     to "{oops",
            "number.json"      to "42",
            "null.json"        to "null",
            "string.json"      to "\"hello\"",
        )
        for ((name, content) in variants) {
            writeFile(name, content)
            pinType(name, "json")
            val file = loadFile(name)

            val flat = file.getFlatContent()

            assertNotNull(flat, "$name: flat content should not be null")
            assertEquals(setOf("$"), flat!!.keys, "$name: fallback exposes only the root")
            assertEquals(content, flat["$"].toString().let {
                (flat["$"] as de.marhali.json5.Json5Primitive).asString
            }, "$name: root carries the raw content")
            assertEquals(TextContentType, file.effectiveContentType(), "$name: effective type is text")
            assertEquals("json", storedContentType(name), "$name: stored metadata is NOT rewritten")
            dir.resolve(name).toFile().delete()
            dir.resolve("mocfsmetas").toFile().deleteRecursively()
        }
    }

    @Test
    fun `structured type recovers automatically when content parses again`() {
        writeFile("f.json", "")
        pinType("f.json", "json")
        assertEquals(TextContentType, loadFile("f.json").also { it.getFlatContent() }.effectiveContentType())

        writeFile("f.json", """{"a": 1}""")
        val file = loadFile("f.json")
        val flat = file.getFlatContent()

        assertEquals(setOf("$", "$['a']"), flat!!.keys)
        assertEquals(JsonContentType, file.effectiveContentType())
        assertEquals("json", storedContentType("f.json"))
    }

    @Test
    fun `valid pinned-json content never engages the fallback`() {
        writeFile("f.json", """{"a": 1}""")
        pinType("f.json", "json")
        val file = loadFile("f.json")

        assertEquals(setOf("$", "$['a']"), file.getFlatContent()!!.keys)
        assertEquals(JsonContentType, file.effectiveContentType())
    }

    // ── 1.3 diff-level ────────────────────────────────────────────────────────

    @Test
    fun `emptied file diffs at the root, not as a deletion`() {
        val refDir = Files.createTempDirectory("moc-fallback-ref-")
        try {
            refDir.resolve("f.json").toFile().writeText("""{"a": 1, "b": {"c": "x"}}""")
            refDir.resolve("mocfsmetas").toFile().mkdirs()
            refDir.resolve("mocfsmetas/mocmetadata.json").toFile()
                .writeText("""{ "f.json": { "encoding": "UTF-8", "content": "json" } }""")

            writeFile("f.json", "")
            pinType("f.json", "json")

            val d = MocFileSystem(dir).diffFrom(MocFileSystem(refDir))

            assertEquals(setOf(Path.of("f.json")), d.getChangedPaths(), "emptied file is CHANGED, not DELETED")
            assertTrue(d.getDeletedPaths().isEmpty())

            val flat = d[Path.of("f.json")]!!.flatContentDiff
            assertFalse(flat.containsKey(""), "no file-deletion marker for an existing file")
            val root = flat["$"]
            assertTrue(root is OptionDiff.Changed, "root carries a Changed record")
            assertEquals("", ((root as OptionDiff.Changed).newValue as de.marhali.json5.Json5Primitive).asString)
            assertTrue(flat["$['a']"] is OptionDiff.Deleted)
            assertTrue(flat["$['b']"] is OptionDiff.Deleted)
            assertFalse(flat.containsKey("$['b']['c']"), "rationalized: nothing under a Deleted path")
        } finally {
            refDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `genuine file deletion still yields the empty-path marker`() {
        val refDir = Files.createTempDirectory("moc-fallback-ref-")
        try {
            refDir.resolve("gone.json").toFile().writeText("""{"a": 1}""")

            val d = MocFileSystem(dir).diffFrom(MocFileSystem(refDir))

            assertEquals(setOf(Path.of("gone.json")), d.getDeletedPaths())
            val flat = d[Path.of("gone.json")]!!.flatContentDiff
            assertTrue(flat[""] is OptionDiff.Deleted)
            assertEquals(1, flat.size)
        } finally {
            refDir.toFile().deleteRecursively()
        }
    }
}
