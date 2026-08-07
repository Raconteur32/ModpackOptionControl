package fr.raconteur.moc.web.routing

import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.versioning.PatchList
import fr.raconteur.moc.versioning.PatchMode
import fr.raconteur.moc.web.RecompositionDraft
import fr.raconteur.moc.web.DiffTreeBuilder
import fr.raconteur.moc.web.EventBus
import fr.raconteur.moc.web.FileSummary
import fr.raconteur.moc.web.FinalizeRequest
import fr.raconteur.moc.web.IgnoreEntry
import fr.raconteur.moc.web.IgnoreStore
import fr.raconteur.moc.web.RecompConflictDto
import fr.raconteur.moc.web.RecompEntryRequest
import fr.raconteur.moc.web.RemoveEntryRequest
import fr.raconteur.moc.web.StartRecompRequest
import fr.raconteur.moc.web.buildDeletedFileTree
import fr.raconteur.moc.web.buildFileSummaries
import fr.raconteur.moc.web.fileKindName
import fr.raconteur.moc.web.isFileIgnored
import fr.raconteur.moc.web.resolveIgnoreAction
import fr.raconteur.moc.web.toDraftEntryDto
import fr.raconteur.moc.filesystem.FileDiffKind
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path

private val validPatchName = Regex("^[A-Za-z0-9._-]+$")

private fun unresolvedConflictsDto() =
    RecompositionDraft.conflictingEntries.map { (fp, op) -> RecompConflictDto(fp, op) }

private fun stagedCountRecomp(filePath: String): Int =
    RecompositionDraft.entries.count { it.filePath == filePath }

private fun hasUnresolvedRecomp(filePath: String): Boolean =
    RecompositionDraft.conflictingEntries.any { it.first == filePath }

private fun cachedDiffMap(): Map<Path, fr.raconteur.moc.filesystem.MocFileDiff> =
    RecompositionDraft.cachedDiff.associate { it.key to it.value }

fun Routing.recompRoutes() {
    get("/api/recomp") {
        if (!RecompositionDraft.hasActiveDraft()) {
            call.respond(HttpStatusCode.NotFound, emptyMap<String, String>())
            return@get
        }
        call.respond(mapOf(
            "isAmend"             to RecompositionDraft.isAmend,
            "rangeStart"          to RecompositionDraft.rangeStart,
            "rangeEnd"            to RecompositionDraft.rangeEnd,
            "unresolvedConflicts" to unresolvedConflictsDto()
        ))
    }

    post("/api/recomp") {
        val body = call.receive<StartRecompRequest>()
        val allPatches = PatchList.getAll()
        if (body.startIdx < 0 || body.endIdx < body.startIdx || body.endIdx >= allPatches.size) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "index_out_of_range"))
            return@post
        }
        IgnoreStore.clearRecompIgnores()
        RecompositionDraft.build(body.startIdx, body.endIdx, body.isAmend)
        // Starting a session doesn't touch McInstance(Ref)MocFileSystem — only the
        // recomp-scoped before/after virtual filesystems — so /api/diff is unaffected;
        // recomp_changed alone covers the /api/recomp* re-fetches the client needs.
        EventBus.broadcast("recomp_changed")
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }

    delete("/api/recomp") {
        if (!RecompositionDraft.hasActiveDraft()) {
            call.respond(HttpStatusCode.NotFound, emptyMap<String, String>())
            return@delete
        }
        RecompositionDraft.clear()
        IgnoreStore.clearRecompIgnores()
        EventBus.broadcast("recomp_changed")
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }

    post("/api/recomp/finalize") {
        val body = call.receive<FinalizeRequest>()
        val start = RecompositionDraft.rangeStart
        val end   = RecompositionDraft.rangeEnd
        val rangeNames = if (start != null && end != null) PatchList.getAll().subList(start, end + 1) else emptyList()
        val error = when {
            start == null || end == null                              -> "no_active_session"
            RecompositionDraft.conflictingEntries.isNotEmpty()         -> "unresolved_conflicts"
            RecompositionDraft.entries.isEmpty()                       -> "empty_draft"
            !validPatchName.matches(body.name)                         -> "invalid_name"
            PatchList.contains(body.name) && body.name !in rangeNames  -> "name_taken"
            else -> null
        }
        if (error != null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to error))
            return@post
        }
        RecompositionDraft.finalize(body.name)
        IgnoreStore.clearRecompIgnores()
        EventBus.broadcast("patches_changed")
        EventBus.broadcast("recomp_changed")
        EventBus.broadcast("diff_changed")
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }

    get("/api/recomp/diff") {
        if (!RecompositionDraft.hasActiveDraft()) {
            call.respond(HttpStatusCode.NotFound, emptyMap<String, String>())
            return@get
        }
        val files = buildFileSummaries(cachedDiffMap(), ::stagedCountRecomp, ::hasUnresolvedRecomp)
        call.respond(mapOf("files" to files))
    }

    get("/api/recomp/diff/{file}") {
        if (!RecompositionDraft.hasActiveDraft()) {
            call.respond(HttpStatusCode.NotFound, emptyMap<String, String>())
            return@get
        }
        val filePath = call.parameters["file"] ?: ""
        val diff = cachedDiffMap()
        val fileDiff = diff[Path.of(filePath)]
        if (fileDiff == null) {
            call.respond(HttpStatusCode.NotFound, emptyMap<String, String>())
            return@get
        }

        val summary = FileSummary(
            path          = filePath,
            kind          = fileKindName(fileDiff.kind),
            stagedCount   = stagedCountRecomp(filePath),
            hasUnresolved = hasUnresolvedRecomp(filePath),
            ignored       = isFileIgnored(filePath, fileDiff)
        )

        val unresolvedSet = RecompositionDraft.conflictingEntries
        val sourceMap = RecompositionDraft.sourceMap.entries
            .filter { it.key.first == filePath }
            .associate { it.key.second to it.value }

        val resolveAction: (String) -> Pair<String?, String?> = { optionPath ->
            val mode = RecompositionDraft.entryFor(filePath, optionPath)?.mode?.name
            resolveIgnoreAction(filePath, optionPath, mode, fileDiff.flatContentDiff[optionPath]?.newValue)
        }

        val unresolvedForFile = unresolvedSet.filter { it.first == filePath }.map { it.second }.toSet()
        val tree = if (fileDiff.kind == FileDiffKind.DELETED)
            buildDeletedFileTree(fileDiff.flatContentDiff, resolveAction, unresolvedForFile, sourceMap)
        else
            DiffTreeBuilder.buildTree(fileDiff.flatContentDiff, resolveAction, unresolvedForFile, sourceMap)

        call.respond(mapOf("file" to summary, "tree" to tree))
    }

    get("/api/recomp/entries") {
        val entries = RecompositionDraft.entries.map { entry ->
            val source = RecompositionDraft.sourceMap[entry.filePath to entry.optionPath]
            entry.toDraftEntryDto(source)
        }
        call.respond(mapOf("entries" to entries))
    }

    post("/api/recomp/entries") {
        if (!RecompositionDraft.hasActiveDraft()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "no_active_session"))
            return@post
        }
        val body = call.receive<RecompEntryRequest>()
        val wasConflicting = (body.filePath to body.optionPath) in RecompositionDraft.conflictingEntries

        if (body.action == "ignore") {
            // DIRECTORY is a filesystem-wide concept (MocSettings.ignoredPaths), so it goes
            // through the general applyIgnore regardless of mode. Every other kind is scoped
            // to this recomposition session — IgnoreStore.recompositionIgnores is kind-less
            // (see RecompIgnoreRequest), so SESSION/VALUE/PERMANENT all collapse to the same
            // recomp-scoped ignore rather than the general, cross-session IgnoreStore lists.
            if (body.kind == "DIRECTORY") {
                applyIgnore(body.filePath, body.optionPath, body.kind, null)
            } else {
                IgnoreStore.addRecomp(IgnoreEntry(body.filePath, body.optionPath))
            }
            RecompositionDraft.resolveConflict(body.filePath, body.optionPath)
            // Mirrors POST /api/ignores: an ignore was added (all kinds), and DIRECTORY
            // additionally reloads the filesystems and changes the main diff. recomp_changed
            // is reserved for start/cancel/finalize of the session (tech §3) — not emitted here.
            EventBus.broadcast("ignores_changed")
            if (body.kind == "DIRECTORY") EventBus.broadcast("diff_changed")
            EventBus.broadcast("draft_changed")
            EventBus.broadcast("conflicts_changed", mapOf("count" to RecompositionDraft.conflictingEntries.size))
            call.respond(HttpStatusCode.OK, emptyMap<String, String>())
            return@post
        }

        val diff = cachedDiffMap()
        val optDiff = diff[Path.of(body.filePath)]?.flatContentDiff?.get(body.optionPath)
        if (optDiff == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "option_not_in_diff"))
            return@post
        }
        val mode = PatchMode.valueOf(body.mode ?: "DEFAULT")
        when (optDiff) {
            is OptionDiff.New     -> RecompositionDraft.setValueEntry(optDiff.filePath, optDiff.path, optDiff.oldValue, optDiff.newValue, mode)
            is OptionDiff.Changed -> RecompositionDraft.setValueEntry(optDiff.filePath, optDiff.path, optDiff.oldValue, optDiff.newValue, mode)
            is OptionDiff.Deleted -> RecompositionDraft.setDeletionEntry(optDiff.filePath, optDiff.path, optDiff.oldValue, mode)
        }
        RecompositionDraft.resolveConflict(body.filePath, body.optionPath)
        EventBus.broadcast("draft_changed")
        if (wasConflicting) EventBus.broadcast("conflicts_changed", mapOf("count" to RecompositionDraft.conflictingEntries.size))
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }

    delete("/api/recomp/entries") {
        val body = call.receive<RemoveEntryRequest>()
        RecompositionDraft.removeEntry(body.filePath, body.optionPath)
        EventBus.broadcast("draft_changed")
        call.respond(HttpStatusCode.OK, emptyMap<String, String>())
    }
}
