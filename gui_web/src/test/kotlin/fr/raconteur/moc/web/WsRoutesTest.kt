package fr.raconteur.moc.web

import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class WsRoutesTest : WebTestBase() {

    @Test
    fun `connecting to ws and receiving a broadcast event works`() = testApplication {
        application {
            mocModule()
        }
        val client = createClient {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }

        client.webSocket("/ws") {
            EventBus.broadcast("diff_changed")
            val frame = withTimeout(5000) { incoming.receive() }
            assertTrue(frame is Frame.Text)
            assertTrue((frame as Frame.Text).readText().contains("\"type\":\"diff_changed\""))
        }
    }
}
