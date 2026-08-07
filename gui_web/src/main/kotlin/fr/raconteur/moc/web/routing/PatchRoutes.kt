package fr.raconteur.moc.web.routing

import fr.raconteur.moc.versioning.Patch
import fr.raconteur.moc.versioning.PatchList
import fr.raconteur.moc.web.DeletePatchesRequest
import fr.raconteur.moc.web.EventBus
import fr.raconteur.moc.web.toDraftEntryDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.patchRoutes() {
    get("/api/patches") {
        val patches = PatchList.getAll().map { name ->
            mapOf("name" to name, "entryCount" to Patch.load(name).entries.size)
        }
        call.respond(mapOf("patches" to patches))
    }

    get("/api/patches/{name}") {
        val name = call.parameters["name"] ?: ""
        if (!PatchList.contains(name)) {
            call.respond(HttpStatusCode.NotFound, emptyMap<String, String>())
            return@get
        }
        val patch = Patch.load(name)
        call.respond(mapOf("name" to patch.name, "entries" to patch.entries.map { it.toDraftEntryDto() }))
    }

    delete("/api/patches") {
        val body = call.receive<DeletePatchesRequest>()
        val missing = body.names.filterNot { PatchList.contains(it) }
        if (missing.isNotEmpty()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "patch_not_found", "missing" to missing))
            return@delete
        }
        body.names.forEach { PatchList.delete(it) }
        EventBus.broadcast("patches_changed")
        EventBus.broadcast("diff_changed")
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }
}
