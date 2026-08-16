package fr.raconteur.moc.web

import de.marhali.json5.Json5Element
import de.marhali.json5.Json5Object
import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.filesystem.FileDiffKind
import fr.raconteur.moc.filesystem.MocFileDiff
import fr.raconteur.moc.filesystem.directChildren
import fr.raconteur.moc.versioning.PatchEntry
import java.nio.file.Path

// Recursively converts a value that may contain Json5Element (as found in OptionDiff.old/newValue,
// since FlatContent stores raw Json5Element instances) into plain Kotlin values that Gson can
// serialize directly. common's own `json5ToNative` performs the same conversion but is `internal`
// to the common module and therefore not visible here.
fun Any?.toPlainValue(): Any? = when (this) {
    null -> null
    is Json5Element -> when {
        isJson5Null      -> null
        isJson5Primitive -> asJson5Primitive.let { p ->
            when {
                p.isBoolean -> p.asBoolean
                p.isNumber  -> p.asNumber
                else        -> p.asString
            }
        }
        isJson5Object -> asJson5Object.entrySet().associate { (k, v) -> k to v.toPlainValue() }
        isJson5Array  -> asJson5Array.asList().map { it.toPlainValue() }
        else -> null
    }
    is Map<*, *> -> mapValues { (_, v) -> v.toPlainValue() }
    is List<*>   -> map { it.toPlainValue() }
    else -> this
}

// Last path segment for display, e.g. "$['client']['maxFps']" -> "maxFps", "$[3]" -> "3".
fun extractLabel(path: String): String {
    if (path.isEmpty()) return "(file)"
    if (path == "$") return "$"
    if (!path.endsWith("]")) return path
    val lastBracket = path.lastIndexOf('[')
    if (lastBracket < 0) return path
    return path.substring(lastBracket + 1, path.length - 1)
        .trim('\'', '"')
        .replace("\\'", "'")
        .replace("\\\\", "\\")
}

internal fun optionDiffKind(optDiff: OptionDiff?): String = when (optDiff) {
    is OptionDiff.New     -> "NEW"
    is OptionDiff.Deleted -> "DELETED"
    is OptionDiff.Changed -> "CHANGED"
    null                  -> "CHANGED"
}

// Builds the recursive DiffNode tree for the children of `parent` (e.g. "$" for the root).
// `resolveAction` returns (action, ignoreKind) for a given option path.
object DiffTreeBuilder {
    fun buildChildren(
        flatDiff: Map<String, OptionDiff>,
        parent: String,
        resolveAction: (path: String) -> Pair<String?, String?>,
        unresolved: Set<String> = emptySet(),
        sourceMap: Map<String, String> = emptyMap()
    ): List<DiffNode> {
        val allPaths = flatDiff.keys.toList()
        return directChildren(allPaths, parent).sorted().map { path ->
            buildNode(flatDiff, path, resolveAction, unresolved, sourceMap)
        }
    }

    // Builds the full per-file tree, always rooted at a single root node: "" (the
    // file-deletion marker) when present, else "$". The diff engine records a Changed
    // entry at "$" for ANY leaf change beneath an object root (container summary) —
    // that root node is structural: it carries its children but no values of its own
    // (see buildNode's leaf-only value rule).
    //
    // An atomic root replacement (root Changed to a non-object value) is displayed as
    // a SINGLE leaf node: the Deleted records of former children stay in the flat
    // diff (they drive DEFAULT-deletion matching at apply time) but are redundant
    // with the root replacement on screen.
    fun buildTree(
        flatDiff: Map<String, OptionDiff>,
        resolveAction: (path: String) -> Pair<String?, String?>,
        unresolved: Set<String> = emptySet(),
        sourceMap: Map<String, String> = emptyMap()
    ): List<DiffNode> {
        val rootPath = if (flatDiff.containsKey("")) "" else "$"
        if (flatDiff[rootPath] == null) return emptyList()
        return listOf(buildNode(flatDiff, rootPath, resolveAction, unresolved, sourceMap))
    }

    private fun buildNode(
        flatDiff: Map<String, OptionDiff>,
        path: String,
        resolveAction: (path: String) -> Pair<String?, String?>,
        unresolved: Set<String>,
        sourceMap: Map<String, String>
    ): DiffNode {
        val optDiff  = flatDiff[path]
        // An atomic replacement (Changed to a non-object value) makes every former
        // child implicitly deleted: the Deleted records stay in the flat diff (they
        // drive DEFAULT-deletion matching at apply time) but are not displayed.
        val isAtomicReplacement = optDiff is OptionDiff.Changed && optDiff.newValue !is Json5Object
        val children = if (isAtomicReplacement) emptyList()
                       else buildChildren(flatDiff, path, resolveAction, unresolved, sourceMap)
        val (action, ignoreKind) = resolveAction(path)
        // Leaf-only value rule: a node with children carries no old/new values in the
        // payload — its container value would duplicate the whole subtree (twice, for
        // the root) for zero display value, since the client only renders leaf values.
        val hasChildren = children.isNotEmpty()
        return DiffNode(
            path        = path,
            label       = extractLabel(path),
            kind        = optionDiffKind(optDiff),
            oldValue    = if (hasChildren) null else optDiff?.oldValue.toPlainValue(),
            newValue    = if (hasChildren) null else optDiff?.newValue.toPlainValue(),
            hasChildren = hasChildren,
            children    = children,
            action      = action,
            ignoreKind  = ignoreKind,
            unresolved  = path in unresolved,
            source      = sourceMap[path]
        )
    }
}

fun fileKindName(kind: FileDiffKind): String = when (kind) {
    FileDiffKind.NEW     -> "NEW"
    FileDiffKind.DELETED -> "DELETED"
    FileDiffKind.CHANGED -> "CHANGED"
}

fun isFileIgnored(filePath: String, fileDiff: MocFileDiff): Boolean =
    if (fileDiff.kind == FileDiffKind.DELETED)
        IgnoreStore.isIgnored(filePath, "", fileDiff.flatContentDiff[""]?.newValue)
    else
        IgnoreStore.isIgnored(filePath, "$", fileDiff.flatContentDiff["$"]?.newValue)

// Resolves the (action, ignoreKind) pair for a single option node, for use as a
// Compares a diff's raw (possibly Json5Element) new value against a VALUE-ignore's
// stored targetValue string. Plain string equality after toPlainValue() covers most
// cases, but numeric values need a numeric-aware fallback: the frontend's targetValue
// comes from JS's `String(jsonNumber)` (e.g. "1"), which drops trailing ".0" that
// Kotlin's Double.toString() keeps (e.g. "1.0") — without this, VALUE ignores on
// whole-number float options could never match.
fun matchesTargetValue(newValue: Any?, targetValue: String?): Boolean {
    if (targetValue == null) return false
    val plain = newValue.toPlainValue()
    val newStr = plain?.toString()
    if (newStr == targetValue) return true
    val newNum = (plain as? Number)?.toDouble() ?: newStr?.toDoubleOrNull()
    val targetNum = targetValue.toDoubleOrNull()
    return newNum != null && targetNum != null && newNum == targetNum
}

// DiffTreeBuilder.resolveAction implementation. `mode` is the staged DraftEntry.mode
// for this option, if any (OVERRIDE/DEFAULT), and `newValue` is the option's current
// new diff value (needed to match VALUE-kind ignores by targetValue).
fun resolveIgnoreAction(filePath: String, optionPath: String, mode: String?, newValue: Any?): Pair<String?, String?> {
    if (mode != null) return mode to null
    if (IgnoreStore.sessionIgnores.any { it.filePath == filePath && it.optionPath == optionPath })
        return "IGNORE" to "SESSION"
    if (IgnoreStore.permanentIgnores.any { it.filePath == filePath && it.optionPath == optionPath })
        return "IGNORE" to "PERMANENT"
    if (IgnoreStore.valueIgnores.any { it.filePath == filePath && it.optionPath == optionPath && matchesTargetValue(newValue, it.targetValue) })
        return "IGNORE" to "VALUE"
    if (IgnoreStore.isIgnoredForRecomp(filePath, optionPath))
        return "IGNORE" to "RECOMP"
    return null to null
}

// Builds the sorted FileSummary list for a whole-instance diff (CHANGED, then NEW,
// then DELETED, each group sorted by path — per doc §5.1/§5.4).
fun buildFileSummaries(
    diff: Map<Path, MocFileDiff>,
    stagedCountFor: (filePath: String) -> Int,
    hasUnresolvedFor: (filePath: String) -> Boolean = { false }
): List<FileSummary> {
    val order = mapOf(FileDiffKind.CHANGED to 0, FileDiffKind.NEW to 1, FileDiffKind.DELETED to 2)
    return diff.entries
        .sortedWith(compareBy({ order.getValue(it.value.kind) }, { it.key.toString() }))
        .map { (path, fileDiff) ->
            val fp = path.toString()
            FileSummary(
                path          = fp,
                kind          = fileKindName(fileDiff.kind),
                stagedCount   = stagedCountFor(fp),
                hasUnresolved = hasUnresolvedFor(fp),
                ignored       = isFileIgnored(fp, fileDiff)
            )
        }
}

fun PatchEntry.toDraftEntryDto(source: String? = null) = DraftEntryDto(
    filePath   = filePath,
    optionPath = optionPath,
    mode       = mode.name,
    kind       = kind.name,
    source     = source
)
