package fr.raconteur.moc.filesystem

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
