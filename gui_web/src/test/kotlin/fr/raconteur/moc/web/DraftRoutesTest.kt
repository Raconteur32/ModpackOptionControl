package fr.raconteur.moc.web

import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DraftRoutesTest : WebTestBase() {

    @Test
    fun `GET draft returns empty list initially`() = webTest { client ->
        val res = client.get("/api/draft")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("\"entries\":[]"))
    }

    @Test
    fun `POST draft entries adds entry for valid diff option`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 1}""")
        McInstanceMocFileSystem.reload()

        val res = client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(1, DraftPatch.entries.size)
    }

    @Test
    fun `POST draft entries returns 404 for option not in diff`() = webTest { client ->
        val res = client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"missing.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        assertEquals(HttpStatusCode.NotFound, res.status)
        assertTrue(res.bodyAsText().contains("option_not_in_diff"))
    }

    @Test
    fun `DELETE draft entries removes staged entry`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }

        val res = client.delete("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(0, DraftPatch.entries.size)
    }

    @Test
    fun `DELETE draft clears everything`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }

        val res = client.delete("/api/draft")
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(0, DraftPatch.entries.size)
    }

    @Test
    fun `POST draft finalize returns 400 for empty draft`() = webTest { client ->
        val res = client.post("/api/draft/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"my-patch"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue(res.bodyAsText().contains("empty_draft"))
    }

    @Test
    fun `POST draft finalize returns 400 when name already taken`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        client.post("/api/draft/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"my-patch"}""")
        }
        gameFile("opts.json", """{"x": 99}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        val res = client.post("/api/draft/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"my-patch"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue(res.bodyAsText().contains("name_taken"))
    }

    @Test
    fun `POST draft finalize succeeds and creates patch`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        val res = client.post("/api/draft/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"my-patch"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(0, DraftPatch.entries.size)
        assertTrue(fr.raconteur.moc.versioning.PatchList.contains("my-patch"))
    }

    @Test
    fun `POST draft finalize expires session ignores but keeps value and permanent ones`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        IgnoreStore.add(IgnoreEntry("opts.json", "$['x']"), IgnoreKind.SESSION)
        IgnoreStore.add(IgnoreEntry("opts.json", "$['x']", "42"), IgnoreKind.VALUE)
        IgnoreStore.add(IgnoreEntry("other.json", "$['z']"), IgnoreKind.PERMANENT)

        val res = client.post("/api/draft/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"my-patch"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(0, IgnoreStore.sessionIgnores.size)
        assertEquals(1, IgnoreStore.valueIgnores.size)
        assertEquals(1, IgnoreStore.permanentIgnores.size)
    }

    @Test
    fun `POST draft finalize-for-amend expires session ignores`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        client.post("/api/draft/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"first-patch"}""")
        }

        gameFile("opts.json", """{"x": 99}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        IgnoreStore.add(IgnoreEntry("opts.json", "$['x']"), IgnoreKind.SESSION)

        val res = client.post("/api/draft/finalize-for-amend")
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(0, IgnoreStore.sessionIgnores.size)
    }

    @Test
    fun `POST draft finalize with taken name does not expire session ignores`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        client.post("/api/draft/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"my-patch"}""")
        }
        gameFile("opts.json", """{"x": 99}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        IgnoreStore.add(IgnoreEntry("opts.json", "$['x']"), IgnoreKind.SESSION)

        val res = client.post("/api/draft/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"my-patch"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(1, IgnoreStore.sessionIgnores.size)
    }

    @Test
    fun `POST draft finalize-for-amend returns 400 when no patches exist`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        val res = client.post("/api/draft/finalize-for-amend")
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue(res.bodyAsText().contains("no_patches"))
    }

    @Test
    fun `POST draft finalize-for-amend returns lastPatchIndex on success`() = webTest { client ->
        gameFile("opts.json", """{"x": 42}""")
        refFile("opts.json",  """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        client.post("/api/draft/finalize") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"first-patch"}""")
        }

        gameFile("opts.json", """{"x": 99}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/draft/entries") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","mode":"DEFAULT"}""")
        }
        val res = client.post("/api/draft/finalize-for-amend")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("\"lastPatchIndex\":0"))
    }
}
