package fr.raconteur.moc.content

class FlatContentDiff private constructor(
    private val map: MutableMap<String, DiffType>
) : Map<String, DiffType> by map {

    constructor() : this(mutableMapOf())

    fun addNew(path: String, newValue: Any?)                   { map[path] = DiffType.New(path, newValue) }
    fun addDeleted(path: String, oldValue: Any?)               { map[path] = DiffType.Deleted(path, oldValue) }
    fun addChanged(path: String, oldValue: Any?, newValue: Any?) { map[path] = DiffType.Changed(path, oldValue, newValue) }

    fun hasLeaf(path: String): Boolean =
        keys.any { it != path && (it.startsWith(path + ".") || it.startsWith(path + "[")) }

    fun cutBranch(path: String) {
        keys.filter { it != path && (it.startsWith(path + ".") || it.startsWith(path + "[")) }
            .forEach { map.remove(it) }
    }

    fun rationalize() {
        keys.sorted()
            .filter { map[it] is DiffType.Deleted }
            .forEach { cutBranch(it) }
    }

    fun getNewPaths()     = filterValues { it is DiffType.New }.keys
    fun getDeletedPaths() = filterValues { it is DiffType.Deleted }.keys
    fun getChangedPaths() = filterValues { it is DiffType.Changed }.keys
}
