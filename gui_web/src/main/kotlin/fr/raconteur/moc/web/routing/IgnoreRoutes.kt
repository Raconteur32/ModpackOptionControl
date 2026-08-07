package fr.raconteur.moc.web.routing

import fr.raconteur.moc.MocSettings
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.web.McInstanceRefMocFileSystem
import fr.raconteur.moc.web.DraftPatch
import fr.raconteur.moc.web.AddIgnoreRequest
import fr.raconteur.moc.web.EventBus
import fr.raconteur.moc.web.IgnoreEntry
import fr.raconteur.moc.web.IgnoreEntryDto
import fr.raconteur.moc.web.IgnoreKind
import fr.raconteur.moc.web.IgnoreStore
import fr.raconteur.moc.web.RecompIgnoreRequest
import fr.raconteur.moc.web.RemoveIgnoreRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// Shared by /api/ignores and the ignore-resolution branch of POST /api/recomp/entries
// (both accept the same 4 kinds, including DIRECTORY which is not stored in IgnoreStore
// itself but delegates to MocSettings.ignoredPaths).
fun applyIgnore(filePath: String, optionPath: String, kind: String, targetValue: String?) {
    if (kind == "DIRECTORY") {
        MocSettings.addIgnoredPath(filePath)
        DraftPatch.removeEntriesUnder(filePath)
        McInstanceMocFileSystem.reload()
        McInstanceRefMocFileSystem.reload()
    } else {
        IgnoreStore.add(IgnoreEntry(filePath, optionPath, targetValue), IgnoreKind.valueOf(kind))
    }
}

fun removeIgnore(filePath: String, optionPath: String, kind: String) {
    if (kind == "DIRECTORY") {
        MocSettings.removeIgnoredPath(filePath)
        McInstanceMocFileSystem.reload()
        McInstanceRefMocFileSystem.reload()
    } else {
        IgnoreStore.remove(filePath, optionPath, IgnoreKind.valueOf(kind))
    }
}

fun Routing.ignoreRoutes() {
    get("/api/ignores") {
        val entries = (IgnoreStore.sessionIgnores.map { it to "SESSION" } +
                IgnoreStore.valueIgnores.map { it to "VALUE" } +
                IgnoreStore.permanentIgnores.map { it to "PERMANENT" })
            .map { (e, kind) -> IgnoreEntryDto(e.filePath, e.optionPath, kind, e.targetValue) }
        call.respond(mapOf(
            "entries" to entries,
            "directories" to MocSettings.ignoredPaths.map { it.toString() }
        ))
    }

    post("/api/ignores") {
        val body = call.receive<AddIgnoreRequest>()
        applyIgnore(body.filePath, body.optionPath, body.kind, body.targetValue)
        EventBus.broadcast("ignores_changed")
        // diff_changed only for DIRECTORY: it's the only kind that reloads the filesystems
        // (per tech §3 event table); entry-level ignores don't change the diff itself, only
        // its ignored/action annotations, already covered by ignores_changed.
        if (body.kind == "DIRECTORY") EventBus.broadcast("diff_changed")
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }

    delete("/api/ignores") {
        val body = call.receive<RemoveIgnoreRequest>()
        removeIgnore(body.filePath, body.optionPath, body.kind)
        EventBus.broadcast("ignores_changed")
        if (body.kind == "DIRECTORY") EventBus.broadcast("diff_changed")
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }

    get("/api/ignores/recomp") {
        val entries = IgnoreStore.recompositionIgnores.map { mapOf("filePath" to it.filePath, "optionPath" to it.optionPath) }
        call.respond(mapOf("entries" to entries))
    }

    post("/api/ignores/recomp") {
        val body = call.receive<RecompIgnoreRequest>()
        IgnoreStore.addRecomp(IgnoreEntry(body.filePath, body.optionPath))
        EventBus.broadcast("ignores_changed")
        EventBus.broadcast("recomp_changed")
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }

    delete("/api/ignores/recomp") {
        val body = call.receive<RecompIgnoreRequest>()
        IgnoreStore.removeRecomp(body.filePath, body.optionPath)
        EventBus.broadcast("ignores_changed")
        EventBus.broadcast("recomp_changed")
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }
}
