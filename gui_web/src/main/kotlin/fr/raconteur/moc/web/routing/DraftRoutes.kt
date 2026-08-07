package fr.raconteur.moc.web.routing

import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.web.McInstanceRefMocFileSystem
import fr.raconteur.moc.web.DraftPatch
import fr.raconteur.moc.versioning.PatchList
import fr.raconteur.moc.versioning.PatchMode
import fr.raconteur.moc.web.EventBus
import fr.raconteur.moc.web.IgnoreStore
import fr.raconteur.moc.web.DraftEntryRequest
import fr.raconteur.moc.web.FinalizeRequest
import fr.raconteur.moc.web.RemoveEntryRequest
import fr.raconteur.moc.web.toDraftEntryDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path

// Matches DraftPatch.finalize's own name constraints (alphanumeric, dot, dash,
// underscore) so invalid_name can be reported before attempting to write the patch.
private val validPatchName = Regex("^[A-Za-z0-9._-]+$")

fun Routing.draftRoutes() {
    get("/api/draft") {
        call.respond(mapOf("entries" to DraftPatch.entries.map { it.toDraftEntryDto() }))
    }

    post("/api/draft/entries") {
        val body = call.receive<DraftEntryRequest>()
        val diff = McInstanceMocFileSystem.diffFrom(McInstanceRefMocFileSystem)
        val optDiff = diff[Path.of(body.filePath)]?.flatContentDiff?.get(body.optionPath)
        if (optDiff == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "option_not_in_diff"))
            return@post
        }
        val mode = PatchMode.valueOf(body.mode)
        when (optDiff) {
            is OptionDiff.New     -> DraftPatch.setValueEntry(optDiff, mode)
            is OptionDiff.Changed -> DraftPatch.setValueEntry(optDiff, mode)
            is OptionDiff.Deleted -> DraftPatch.setDeletionEntry(optDiff, mode)
        }
        EventBus.broadcast("draft_changed")
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }

    delete("/api/draft/entries") {
        val body = call.receive<RemoveEntryRequest>()
        DraftPatch.removeEntry(body.filePath, body.optionPath)
        EventBus.broadcast("draft_changed")
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }

    delete("/api/draft") {
        DraftPatch.clear()
        EventBus.broadcast("draft_changed")
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }

    post("/api/draft/finalize") {
        val body = call.receive<FinalizeRequest>()
        val error = when {
            DraftPatch.entries.isEmpty()            -> "empty_draft"
            !validPatchName.matches(body.name)       -> "invalid_name"
            PatchList.contains(body.name) -> "name_taken"
            else -> null
        }
        if (error != null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to error))
            return@post
        }
        DraftPatch.finalize(body.name)
        IgnoreStore.resetSession()
        EventBus.broadcast("patches_changed")
        EventBus.broadcast("draft_changed")
        EventBus.broadcast("diff_changed")
        EventBus.broadcast("ignores_changed")
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }

    post("/api/draft/finalize-for-amend") {
        val error = when {
            PatchList.getAll().isEmpty() -> "no_patches"
            DraftPatch.entries.isEmpty()                              -> "empty_draft"
            else -> null
        }
        if (error != null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to error))
            return@post
        }
        val lastPatchIndex = DraftPatch.finalizeForAmend()
        IgnoreStore.resetSession()
        EventBus.broadcast("draft_changed")
        EventBus.broadcast("ignores_changed")
        call.respond(mapOf("lastPatchIndex" to lastPatchIndex))
    }
}
