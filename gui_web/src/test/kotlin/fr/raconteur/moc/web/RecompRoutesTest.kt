package fr.raconteur.moc.web

import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.versioning.PatchList
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecompRoutesTest : WebTestBase() {

    // Finalizes a patch that sets opts.json's $['x'] to `value`, from whatever the
    // instance currently holds. Two such patches on the same option produce a
    // RecompositionDraft conflict when both are included in the same range.
    private suspend fun finalizePatch(client: HttpClient, name: String, value: Int) {
        gameFile("opts.json", """{"x": $value}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        client.post("/api/draft/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"$name"}""")
        }
    }

    @Test
    fun `GET recomp returns 404 when no active session`() = webTest { client ->
        val res = client.get("/api/recomp")
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `POST recomp returns index_out_of_range for invalid indices`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizePatch(client, "patch-a", 42)

        val res = client.post("/api/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"startIdx":0,"endIdx":5,"isAmend":false}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue(res.bodyAsText().contains("index_out_of_range"))
    }

    @Test
    fun `POST then GET recomp starts and reports a session`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizePatch(client, "patch-a", 42)

        client.post("/api/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"startIdx":0,"endIdx":0,"isAmend":false}""")
        }
        val res = client.get("/api/recomp")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("\"isAmend\":false"))
        assertTrue(body.contains("\"rangeStart\":0"))
        assertTrue(body.contains("\"rangeEnd\":0"))
    }

    @Test
    fun `DELETE recomp cancels the active session`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizePatch(client, "patch-a", 42)
        client.post("/api/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"startIdx":0,"endIdx":0,"isAmend":false}""")
        }

        val res = client.delete("/api/recomp")
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/recomp").status)
    }

    @Test
    fun `DELETE recomp clears recomposition ignores made during the cancelled session`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizePatch(client, "patch-a", 42)
        client.post("/api/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"startIdx":0,"endIdx":0,"isAmend":false}""")
        }
        client.post("/api/recomp/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","action":"ignore","kind":"SESSION"}""")
        }
        assertTrue(client.get("/api/ignores/recomp").bodyAsText().contains("opts.json"))

        client.delete("/api/recomp")
        assertTrue(client.get("/api/ignores/recomp").bodyAsText().contains("\"entries\":[]"))
    }

    @Test
    fun `DELETE recomp returns 404 when no active session`() = webTest { client ->
        val res = client.delete("/api/recomp")
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `POST recomp finalize blocked by unresolved conflicts`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizePatch(client, "patch-a", 42)
        finalizePatch(client, "patch-b", 99)

        client.post("/api/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"startIdx":0,"endIdx":1,"isAmend":false}""")
        }

        val recompState = client.get("/api/recomp").bodyAsText()
        assertTrue(recompState.contains("\"filePath\":\"opts.json\""))

        val res = client.post("/api/recomp/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"merged"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue(res.bodyAsText().contains("unresolved_conflicts"))
    }

    @Test
    fun `POST recomp entries stage resolves conflict and finalize succeeds`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizePatch(client, "patch-a", 42)
        finalizePatch(client, "patch-b", 99)

        client.post("/api/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"startIdx":0,"endIdx":1,"isAmend":false}""")
        }

        val stageRes = client.post("/api/recomp/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"OVERRIDE"}""")
        }
        assertEquals(HttpStatusCode.OK, stageRes.status)

        val finalizeRes = client.post("/api/recomp/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"merged"}""")
        }
        assertEquals(HttpStatusCode.OK, finalizeRes.status)
        assertTrue(PatchList.contains("merged"))
        assertTrue(!PatchList.contains("patch-a"))
        assertTrue(!PatchList.contains("patch-b"))
        assertEquals(HttpStatusCode.NotFound, client.get("/api/recomp").status)
    }

    @Test
    fun `POST recomp entries with ignore action resolves conflict without staging`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizePatch(client, "patch-a", 42)
        finalizePatch(client, "patch-b", 99)

        client.post("/api/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"startIdx":0,"endIdx":1,"isAmend":false}""")
        }

        val res = client.post("/api/recomp/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","action":"ignore","kind":"SESSION"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)

        val recompState = client.get("/api/recomp").bodyAsText()
        assertTrue(recompState.contains("\"unresolvedConflicts\":[]"))
    }

    // Regression test for a bug where a non-DIRECTORY ignore made through the
    // recomp entries endpoint landed in the general, cross-session IgnoreStore
    // lists instead of IgnoreStore.recompositionIgnores (the [Recomp ignores ▾]
    // popover backing store), so it never showed up there.
    @Test
    fun `POST recomp entries with ignore action stores it as a recomposition-scoped ignore`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizePatch(client, "patch-a", 42)
        finalizePatch(client, "patch-b", 99)

        client.post("/api/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"startIdx":0,"endIdx":1,"isAmend":false}""")
        }
        client.post("/api/recomp/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","action":"ignore","kind":"SESSION"}""")
        }

        val recompIgnores = client.get("/api/ignores/recomp").bodyAsText()
        assertTrue(recompIgnores.contains("\"filePath\":\"opts.json\""))
        // Must NOT have leaked into the general, cross-session ignore list.
        assertTrue(client.get("/api/ignores").bodyAsText().contains("\"entries\":[]"))
    }

    // Recomp-scoped ignores must not leak across sessions: starting a fresh
    // session clears any stale entries left over from a previous one.
    @Test
    fun `POST recomp clears stale recomposition ignores from a previous session`() = webTest { client ->
        client.post("/api/ignores/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"stale.json","optionPath":"$['y']"}""")
        }
        assertTrue(client.get("/api/ignores/recomp").bodyAsText().contains("stale.json"))

        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizePatch(client, "patch-a", 42)
        client.post("/api/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"startIdx":0,"endIdx":0,"isAmend":false}""")
        }

        assertTrue(client.get("/api/ignores/recomp").bodyAsText().contains("\"entries\":[]"))
    }

    // Session ignores expire when the author commits the patch they were building
    // (draft finalize / amend stash) — NOT on history surgery. Recomposition finalize
    // must preserve session ignores while clearing recomposition-scoped ones.
    @Test
    fun `POST recomp finalize preserves session ignores and clears recomposition ignores`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizePatch(client, "patch-a", 42)
        finalizePatch(client, "patch-b", 99)
        IgnoreStore.add(IgnoreEntry("opts.json", "$['x']"), IgnoreKind.SESSION)
        client.post("/api/ignores/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"other.json","optionPath":"$['y']"}""")
        }

        client.post("/api/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"startIdx":0,"endIdx":1,"isAmend":false}""")
        }
        client.post("/api/recomp/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"OVERRIDE"}""")
        }
        val res = client.post("/api/recomp/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"merged"}""")
        }

        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(1, IgnoreStore.sessionIgnores.size)
        assertEquals(0, IgnoreStore.recompositionIgnores.size)
    }

    // Staging an ancestor of an auto-populated entry replaces it and drops its
    // provenance — the overlap invariant is enforced server-side.
    @Test
    fun `POST recomp entries staging an ancestor replaces auto-populated entry and its provenance`() = webTest { client ->
        refFile("opts.json", """{"a": {"b": 1}}""")
        McInstanceMocFileSystem.reload()
        gameFile("opts.json", """{"a": {"b": 42}}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['a']['b']","mode":"DEFAULT"}""")
        }
        client.post("/api/draft/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"patch-c"}""")
        }

        client.post("/api/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"startIdx":0,"endIdx":0,"isAmend":false}""")
        }
        assertEquals("patch-c", RecompositionDraft.sourceMap["opts.json" to "$['a']['b']"],
            "precondition: entry auto-populated with provenance")

        val res = client.post("/api/recomp/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['a']","mode":"OVERRIDE"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(listOf("$['a']"), RecompositionDraft.entries.map { it.optionPath })
        assertTrue(RecompositionDraft.sourceMap.isEmpty(),
            "removed entry's provenance must be discarded with it")
    }

    @Test
    fun `GET recomp diff returns 404 without an active session`() = webTest { client ->
        val res = client.get("/api/recomp/diff")
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `GET recomp diff lists files for the active session`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizePatch(client, "patch-a", 42)
        client.post("/api/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"startIdx":0,"endIdx":0,"isAmend":false}""")
        }

        val res = client.get("/api/recomp/diff")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("\"path\":\"opts.json\""))
    }
}
