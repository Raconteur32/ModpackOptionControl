package fr.raconteur.moc.web

import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiffRoutesTest : WebTestBase() {

    @Test
    fun `GET diff returns empty file list initially`() = webTest { client ->
        val res = client.get("/api/diff")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("\"files\":[]"))
    }

    @Test
    fun `GET diff lists a changed file`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 1}""")
        McInstanceMocFileSystem.reload()

        val res = client.get("/api/diff")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("\"path\":\"opts.json\""))
        assertTrue(res.bodyAsText().contains("\"kind\":\"CHANGED\""))
    }

    @Test
    fun `GET diff file returns 404 for unknown file`() = webTest { client ->
        val res = client.get("/api/diff/${"missing.json".encodeURLPathPart()}")
        assertEquals(HttpStatusCode.NotFound, res.status)
        assertTrue(res.bodyAsText().contains("file_not_found"))
    }

    @Test
    fun `GET diff file returns tree for known file`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 1}""")
        McInstanceMocFileSystem.reload()

        val res = client.get("/api/diff/${"opts.json".encodeURLPathPart()}")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("\"path\":\"opts.json\""))
        assertTrue(body.contains("\"tree\""))
    }

    @Test
    fun `GET diff with showAll includes options unchanged from ref`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 42}""")
        McInstanceMocFileSystem.reload()

        val resDefault = client.get("/api/diff")
        assertTrue(resDefault.bodyAsText().contains("\"files\":[]"))

        val resAll = client.get("/api/diff?showAll=true")
        assertTrue(resAll.bodyAsText().contains("\"path\":\"opts.json\""))
        assertTrue(resAll.bodyAsText().contains("\"kind\":\"NEW\""))
    }

    // ── Root-node rendering (fix-emptied-file-diff) ───────────────────────────

    /** Pins the file as json in live metadata (loaded while valid), then replaces its content. */
    private fun pinThenRewrite(name: String, initial: String, rewritten: String) {
        gameFile(name, initial)
        McInstanceMocFileSystem.reload()
        gameFile(name, rewritten)
        McInstanceMocFileSystem.reload()
    }

    @Test
    fun `emptied JSON file shows a single CHANGED root node and can be staged`() = webTest { client ->
        pinThenRewrite("emptied.json", """{"a": 1, "b": {"c": "x"}}""", "")
        refFile("emptied.json", """{"a": 1, "b": {"c": "x"}}""")

        val res = client.get("/api/diff/${"emptied.json".encodeURLPathPart()}")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains(""""kind":"CHANGED""""), body)
        assertTrue(body.contains(""""path":"$""""), "root node must be present: $body")
        assertTrue(body.contains(""""label":"$""""), body)
        assertTrue(body.contains(""""newValue":"""""), "root new value is the empty string: $body")
        assertFalse(body.contains("$['a']"), "atomic root replacement is a single node, former children stay hidden: $body")
        assertFalse(body.contains("$['b']"), body)

        val staged = client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"emptied.json","optionPath":"$","mode":"DEFAULT"}""")
        }
        assertEquals(HttpStatusCode.OK, staged.status, "root of an emptied file must be stageable")
    }

    @Test
    fun `root replaced by an empty array is visible as a root node`() = webTest { client ->
        pinThenRewrite("arr.json", """{"a": 1}""", "[]")
        refFile("arr.json", """{"a": 1}""")

        val res = client.get("/api/diff/${"arr.json".encodeURLPathPart()}")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains(""""path":"$""""), "root node must be present: $body")
        assertTrue(body.contains(""""newValue":[]"""), body)
        assertFalse(body.contains("$['a']"), body)
    }

    // Gson escapes single quotes as ' in path fields — parse the payload
    // instead of string-matching paths containing quotes.
    private fun treePaths(body: String): List<String> {
        val tree = com.google.gson.JsonParser.parseString(body)
            .asJsonObject.getAsJsonArray("tree")
        val paths = mutableListOf<String>()
        fun walk(nodes: com.google.gson.JsonArray) {
            for (n in nodes) {
                val node = n.asJsonObject
                paths += node.get("path").asString
                node.getAsJsonArray("children")?.let(::walk)
            }
        }
        walk(tree)
        return paths
    }

    @Test
    fun `nested object replaced by a string shows no redundant deleted children`() = webTest { client ->
        pinThenRewrite("nested.json", """{"test": {"test": "test"}}""", """{"test": "test"}""")
        refFile("nested.json", """{"test": {"test": "test"}}""")

        val res = client.get("/api/diff/${"nested.json".encodeURLPathPart()}")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertEquals(listOf("$", "$['test']"), treePaths(body),
            "former children of an atomically replaced node stay hidden: $body")
        assertTrue(body.contains(""""newValue":"test""""), body)
    }

    @Test
    fun `deleted key under an object root shows a single deleted node`() = webTest { client ->
        pinThenRewrite("nested.json", """{"test": {"test": "test"}}""", """{}""")
        refFile("nested.json", """{"test": {"test": "test"}}""")

        val res = client.get("/api/diff/${"nested.json".encodeURLPathPart()}")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertEquals(listOf("$", "$['test']"), treePaths(body),
            "root node is structural, the deleted child hangs beneath it: $body")
        assertTrue(body.contains(""""kind":"DELETED""""), body)
    }

    // Gson omits null fields: a value-less node simply lacks oldValue/newValue.
    private fun rootNode(body: String): com.google.gson.JsonObject =
        com.google.gson.JsonParser.parseString(body).asJsonObject
            .getAsJsonArray("tree").first().asJsonObject

    @Test
    fun `container-summary root is always present with children and no values`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 1}""")
        McInstanceMocFileSystem.reload()

        val res = client.get("/api/diff/${"opts.json".encodeURLPathPart()}")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertEquals(listOf("$", "$['x']"), treePaths(body),
            "the tree is rooted at the container-summary root node: $body")
        val root = rootNode(body)
        assertEquals("CHANGED", root.get("kind").asString)
        assertTrue(root.get("hasChildren").asBoolean, body)
        assertFalse(root.has("oldValue"), "a node with children carries no values: $body")
        assertFalse(root.has("newValue"), "a node with children carries no values: $body")
    }

    @Test
    fun `deleted file shows a single (file) root node`() = webTest { client ->
        refFile("gone.json", """{"a": 1}""")
        McInstanceMocFileSystem.reload()

        val res = client.get("/api/diff/${"gone.json".encodeURLPathPart()}")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertEquals(listOf(""), treePaths(body), "deleted file tree is a single (file) root: $body")
        val root = rootNode(body)
        assertEquals("(file)", root.get("label").asString)
        assertEquals("DELETED", root.get("kind").asString)
        assertFalse(root.get("hasChildren").asBoolean, body)
        assertTrue(root.has("oldValue"), "a deleted leaf carries its former value: $body")
    }

    @Test
    fun `staging the root stages the whole file and replaces staged children`() = webTest { client ->
        gameFile("opts.json", """{"x": 42, "y": 7}""")
        refFile("opts.json",  """{"x": 1, "y": 7}""")
        McInstanceMocFileSystem.reload()

        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        val staged = client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$","mode":"DEFAULT"}""")
        }
        assertEquals(HttpStatusCode.OK, staged.status, "the container-summary root must be stageable")

        val entries = client.get("/api/draft")
        val paths = com.google.gson.JsonParser.parseString(entries.bodyAsText()).asJsonObject
            .getAsJsonArray("entries").map { it.asJsonObject.get("optionPath").asString }
        assertEquals(listOf("$"), paths,
            "staging the root captures the whole file and replaces staged children")
    }
}
