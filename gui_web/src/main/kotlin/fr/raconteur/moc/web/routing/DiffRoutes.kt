package fr.raconteur.moc.web.routing

import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.web.McInstanceRefMocFileSystem
import fr.raconteur.moc.web.DraftPatch
import fr.raconteur.moc.web.DiffTreeBuilder
import fr.raconteur.moc.web.EmptyMocFileSystem
import fr.raconteur.moc.web.IgnoreStore
import fr.raconteur.moc.web.FileSummary
import fr.raconteur.moc.web.buildFileSummaries
import fr.raconteur.moc.web.fileKindName
import fr.raconteur.moc.web.isFileIgnored
import fr.raconteur.moc.web.resolveIgnoreAction
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path

private fun stagedCount(filePath: String): Int = DraftPatch.entries.count { it.filePath == filePath }

// `flatDiff` supplies each option's current new value (needed so VALUE-kind ignores,
// which match by targetValue, can actually resolve to an active IGNORE action).
private fun resolveActionFor(filePath: String, flatDiff: Map<String, OptionDiff>): (path: String) -> Pair<String?, String?> = { optionPath ->
    val mode = DraftPatch.entries.find { it.filePath == filePath && it.optionPath == optionPath }?.mode?.name
    resolveIgnoreAction(filePath, optionPath, mode, flatDiff[optionPath]?.newValue)
}

fun Routing.diffRoutes() {
    get("/api/diff") {
        val showAll = call.request.queryParameters["showAll"] == "true"
        val realDiff = McInstanceMocFileSystem.diffFrom(McInstanceRefMocFileSystem)
        IgnoreStore.pruneStaleValueIgnores(realDiff)

        val diff = if (showAll) McInstanceMocFileSystem.diffFrom(EmptyMocFileSystem) else realDiff
        val files = buildFileSummaries(diff, ::stagedCount)
        call.respond(mapOf("files" to files))
    }

    get("/api/diff/{file}") {
        val filePath = call.parameters["file"] ?: ""
        val showAll = call.request.queryParameters["showAll"] == "true"

        val realDiff = McInstanceMocFileSystem.diffFrom(McInstanceRefMocFileSystem)
        IgnoreStore.pruneStaleValueIgnores(realDiff)

        val diff = if (showAll) McInstanceMocFileSystem.diffFrom(EmptyMocFileSystem) else realDiff
        val fileDiff = diff[Path.of(filePath)]
        if (fileDiff == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "file_not_found"))
            return@get
        }

        val summary = FileSummary(
            path          = filePath,
            kind          = fileKindName(fileDiff.kind),
            stagedCount   = stagedCount(filePath),
            hasUnresolved = false,
            ignored       = isFileIgnored(filePath, fileDiff)
        )

        val tree = DiffTreeBuilder.buildTree(fileDiff.flatContentDiff, resolveActionFor(filePath, fileDiff.flatContentDiff))

        call.respond(mapOf("file" to summary, "tree" to tree))
    }
}
