package fr.raconteur.moc.web

import fr.raconteur.moc.web.routing.diffRoutes
import fr.raconteur.moc.web.routing.draftRoutes
import fr.raconteur.moc.web.routing.ignoreRoutes
import fr.raconteur.moc.web.routing.patchRoutes
import fr.raconteur.moc.web.routing.recompRoutes
import fr.raconteur.moc.web.routing.wsRoutes
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*

fun Application.mocModule() {
    install(ContentNegotiation) { gson() }
    install(WebSockets)
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "bad_request")))
        }
        exception<NoSuchElementException> { call, _ ->
            call.respond(HttpStatusCode.NotFound, emptyMap<String, String>())
        }
    }

    routing {
        staticResources("/", "static")
        diffRoutes()
        draftRoutes()
        patchRoutes()
        recompRoutes()
        ignoreRoutes()
        wsRoutes()
    }
}
