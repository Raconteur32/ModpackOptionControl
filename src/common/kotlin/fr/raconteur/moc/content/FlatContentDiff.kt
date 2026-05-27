package fr.raconteur.moc.content

class FlatContentDiff private constructor(
    val filePath: String,
    private val map: MutableMap<String, OptionDiff>
) : Map<String, OptionDiff> by map {

    constructor(filePath: String) : this(filePath, mutableMapOf())

    fun addNew(path: String, newValue: Any?)                     { map[path] = OptionDiff.New(filePath, path, newValue) }
    fun addDeleted(path: String, oldValue: Any?)                 { map[path] = OptionDiff.Deleted(filePath, path, oldValue) }
    fun addChanged(path: String, oldValue: Any?, newValue: Any?) { map[path] = OptionDiff.Changed(filePath, path, oldValue, newValue) }

    fun hasLeaf(path: String): Boolean =
        keys.any { it != path && (it.startsWith(path + ".") || it.startsWith(path + "[")) }

    fun cutBranch(path: String) {
        keys.filter { it != path && (it.startsWith(path + ".") || it.startsWith(path + "[")) }
            .forEach { map.remove(it) }
    }

    fun rationalize() {
        keys.sorted()
            .filter { map[it] is OptionDiff.Deleted }
            .forEach { cutBranch(it) }
    }

    fun getNewPaths()     = filterValues { it is OptionDiff.New }.keys
    fun getDeletedPaths() = filterValues { it is OptionDiff.Deleted }.keys
    fun getChangedPaths() = filterValues { it is OptionDiff.Changed }.keys
}
