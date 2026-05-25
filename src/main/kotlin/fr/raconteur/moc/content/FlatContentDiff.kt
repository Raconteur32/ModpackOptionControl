package fr.raconteur.moc.content

class FlatContentDiff private constructor(
    private val map: MutableMap<String, DiffType>
) : Map<String, DiffType> by map {

    constructor() : this(mutableMapOf())

    fun addNew(path: String)     { map[path] = DiffType.NEW }
    fun addDeleted(path: String) { map[path] = DiffType.DELETED }
    fun addChanged(path: String) { map[path] = DiffType.CHANGED }

    fun hasLeaf(path: String): Boolean =
        keys.any { it != path && (it.startsWith(path + ".") || it.startsWith(path + "[")) }

    fun cutBranch(path: String) {
        val toRemove = keys.filter { it != path && (it.startsWith(path + ".") || it.startsWith(path + "[")) }
        toRemove.forEach { map.remove(it) }
    }

    fun rationalize() {
        keys.sorted()
            .filter { map[it] == DiffType.DELETED }
            .forEach { cutBranch(it) }
    }

    fun getNewPaths()     = filterValues { it == DiffType.NEW }.keys
    fun getDeletedPaths() = filterValues { it == DiffType.DELETED }.keys
    fun getChangedPaths() = filterValues { it == DiffType.CHANGED }.keys
}
