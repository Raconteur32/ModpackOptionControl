package fr.raconteur.moc.web

import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
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
}
