package fr.raconteur.moc.web

import fr.raconteur.moc.MocSettings
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IgnoreRoutesTest : WebTestBase() {

    @Test
    fun `GET ignores returns empty entries and directories initially`() = webTest { client ->
        val res = client.get("/api/ignores")
        assertEquals(HttpStatusCode.OK, res.status)
        val body = res.bodyAsText()
        assertTrue(body.contains("\"entries\":[]"))
    }

    @Test
    fun `POST ignores with kind SESSION adds an entry without targetValue field`() = webTest { client ->
        val res = client.post("/api/ignores") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","kind":"SESSION"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)

        val list = client.get("/api/ignores").bodyAsText()
        assertTrue(list.contains("\"kind\":\"SESSION\""))
        assertTrue(!list.contains("\"targetValue\""))
    }

    @Test
    fun `POST ignores with kind VALUE keeps targetValue`() = webTest { client ->
        client.post("/api/ignores") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","kind":"VALUE","targetValue":"42"}""")
        }
        val list = client.get("/api/ignores").bodyAsText()
        assertTrue(list.contains("\"targetValue\":\"42\""))
    }

    @Test
    fun `DELETE ignores removes an entry`() = webTest { client ->
        client.post("/api/ignores") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","kind":"SESSION"}""")
        }
        val res = client.delete("/api/ignores") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']","kind":"SESSION"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(client.get("/api/ignores").bodyAsText().contains("\"entries\":[]"))
    }

    @Test
    fun `POST ignores with kind DIRECTORY reloads both filesystems and adds to directories`() = webTest { client ->
        gameFile("ignored-dir/opts.json", """{"x": 42}""")
        McInstanceMocFileSystem.reload()

        val res = client.post("/api/ignores") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"ignored-dir","optionPath":"","kind":"DIRECTORY"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(MocSettings.ignoredPaths.any { it.toString() == "ignored-dir" })

        val list = client.get("/api/ignores").bodyAsText()
        assertTrue(list.contains("\"ignored-dir\""))
        assertTrue(!McInstanceMocFileSystem.hasFile(java.nio.file.Path.of("ignored-dir/opts.json")))
    }

    @Test
    fun `DELETE ignores with kind DIRECTORY removes from MocSettings and reloads`() = webTest { client ->
        gameFile("ignored-dir/opts.json", """{"x": 42}""")
        McInstanceMocFileSystem.reload()
        client.post("/api/ignores") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"ignored-dir","optionPath":"","kind":"DIRECTORY"}""")
        }

        val res = client.delete("/api/ignores") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"ignored-dir","optionPath":"","kind":"DIRECTORY"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(MocSettings.ignoredPaths.none { it.toString() == "ignored-dir" })
        assertTrue(McInstanceMocFileSystem.hasFile(java.nio.file.Path.of("ignored-dir/opts.json")))
    }

    @Test
    fun `GET POST DELETE ignores recomp manage the session-scoped list`() = webTest { client ->
        val getEmpty = client.get("/api/ignores/recomp")
        assertTrue(getEmpty.bodyAsText().contains("\"entries\":[]"))

        client.post("/api/ignores/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']"}""")
        }
        val getOne = client.get("/api/ignores/recomp").bodyAsText()
        assertTrue(getOne.contains("\"filePath\":\"opts.json\""))

        client.delete("/api/ignores/recomp") {
            contentType(ContentType.Application.Json)
            setBody("""{"filePath":"opts.json","optionPath":"$['x']"}""")
        }
        assertTrue(client.get("/api/ignores/recomp").bodyAsText().contains("\"entries\":[]"))
    }
}
