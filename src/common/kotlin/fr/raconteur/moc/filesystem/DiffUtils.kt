package fr.raconteur.moc.filesystem

import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.versioning.DraftPatch
import fr.raconteur.moc.versioning.PatchMode
import java.nio.file.Files

fun directChildren(allPaths: List<String>, parent: String): List<String> =
    allPaths.filter { path ->
        if (path == parent || !path.startsWith(parent)) return@filter false
        val suffix = path.removePrefix(parent)
        when {
            suffix.startsWith('.') -> suffix.drop(1).let { !it.contains('.') && !it.contains('[') }
            suffix.startsWith('[') -> suffix.indexOf(']').let { it != -1 && suffix.drop(it + 1).isEmpty() }
            else -> false
        }
    }

fun isDescendant(childPath: String, parentPath: String): Boolean {
    if (childPath.length <= parentPath.length) return false
    if (!childPath.startsWith(parentPath)) return false
    val c = childPath[parentPath.length]
    return c == '.' || c == '['
}

fun applyDiffToDraft(optDiff: OptionDiff?, mode: PatchMode) = when (optDiff) {
    is OptionDiff.New     -> DraftPatch.setValueEntry(optDiff, mode)
    is OptionDiff.Changed -> DraftPatch.setValueEntry(optDiff, mode)
    is OptionDiff.Deleted -> DraftPatch.setDeletionEntry(optDiff, mode)
    null                  -> Unit
}

fun openIdeDiff(oldValue: Any?, newValue: Any?, extension: String) {
    val suffix = if (extension.startsWith('.')) extension else ".$extension"
    val oldFile = Files.createTempFile("moc-old-", suffix).also { it.toFile().writeText(oldValue?.toString() ?: "") }
    val newFile = Files.createTempFile("moc-new-", suffix).also { it.toFile().writeText(newValue?.toString() ?: "") }
    val command = when {
        System.getenv("TERMINAL_EMULATOR")?.contains("JetBrains", ignoreCase = true) == true ->
            listOf("idea", "diff", oldFile.toString(), newFile.toString())
        System.getenv("TERM_PROGRAM")?.equals("vscode", ignoreCase = true) == true ->
            listOf("code", "--diff", oldFile.toString(), newFile.toString())
        else ->
            listOf("idea", "diff", oldFile.toString(), newFile.toString())
    }
    ProcessBuilder(command).inheritIO().start()
}
