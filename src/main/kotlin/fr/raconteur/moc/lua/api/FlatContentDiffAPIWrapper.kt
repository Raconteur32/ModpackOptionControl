package fr.raconteur.moc.lua.api

import fr.raconteur.moc.content.FlatContentDiff

@Suppress("FunctionName")
class FlatContentDiffAPIWrapper(private val flatContentDiff: FlatContentDiff) {

    fun add_new(path: String)     = flatContentDiff.addNew(path)
    fun add_deleted(path: String) = flatContentDiff.addDeleted(path)
    fun add_changed(path: String) = flatContentDiff.addChanged(path)

    fun has_leaf(path: String): Boolean = flatContentDiff.hasLeaf(path)
    fun cut_branch(path: String)        = flatContentDiff.cutBranch(path)
    fun rationalize()                   = flatContentDiff.rationalize()
}