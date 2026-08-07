package fr.raconteur.moc.web

import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

object EventBus {
    private val sessions = CopyOnWriteArrayList<WebSocketSession>()
    private val scope    = CoroutineScope(Dispatchers.IO)

    fun register(session: WebSocketSession)   { sessions.add(session) }
    fun unregister(session: WebSocketSession) { sessions.remove(session) }

    fun broadcast(event: String, extra: Map<String, Any> = emptyMap()) {
        val payload = buildString {
            append("{\"type\":\"$event\"")
            extra.forEach { (k, v) -> append(",\"$k\":$v") }
            append("}")
        }
        scope.launch {
            sessions.forEach { runCatching { it.send(Frame.Text(payload)) } }
        }
    }
}
