package fr.raconteur.moc.web.routing

import fr.raconteur.moc.web.EventBus
import io.ktor.server.routing.*
import io.ktor.server.websocket.*

fun Routing.wsRoutes() {
    webSocket("/ws") {
        EventBus.register(this)
        try {
            for (frame in incoming) { /* read ignored — server→client only */ }
        } finally {
            EventBus.unregister(this)
        }
    }
}
