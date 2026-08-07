package fr.raconteur.moc.web

import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.versioning.PatchList
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatchRoutesTest : WebTestBase() {

    private suspend fun finalizeSimplePatch(client: io.ktor.client.HttpClient, name: String, value: Int) {
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
    fun `GET patches returns empty list initially`() = webTest { client ->
        val res = client.get("/api/patches")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("\"patches\":[]"))
    }

    @Test
    fun `GET patches lists finalized patches with entry counts`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizeSimplePatch(client, "patch-a", 42)

        val res = client.get("/api/patches")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("\"name\":\"patch-a\""))
        assertTrue(res.bodyAsText().contains("\"entryCount\":1"))
    }

    @Test
    fun `GET patches by name returns 404 for unknown patch`() = webTest { client ->
        val res = client.get("/api/patches/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, res.status)
    }

    @Test
    fun `GET patches by name returns entries`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizeSimplePatch(client, "patch-a", 42)

        val res = client.get("/api/patches/patch-a")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("\"name\":\"patch-a\""))
    }

    @Test
    fun `DELETE patches removes all listed patches`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizeSimplePatch(client, "patch-a", 42)

        val res = client.delete("/api/patches") {
            contentType(ContentType.Application.Json)
            setBody("""{"names":["patch-a"]}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(!PatchList.contains("patch-a"))
    }

    @Test
    fun `DELETE patches returns 400 with missing names when partially absent`() = webTest { client ->
        refFile("opts.json", """{"x": 1}""")
        McInstanceMocFileSystem.reload()
        finalizeSimplePatch(client, "patch-a", 42)

        val res = client.delete("/api/patches") {
            contentType(ContentType.Application.Json)
            setBody("""{"names":["patch-a","does-not-exist"]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertTrue(res.bodyAsText().contains("patch_not_found"))
        assertTrue(res.bodyAsText().contains("does-not-exist"))
        // Atomic: patch-a must NOT have been deleted since the batch failed
        assertTrue(PatchList.contains("patch-a"))
    }
}
