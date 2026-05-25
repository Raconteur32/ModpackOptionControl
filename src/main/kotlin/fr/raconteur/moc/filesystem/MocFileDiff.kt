package fr.raconteur.moc.filesystem

import fr.raconteur.moc.content.DiffType
import fr.raconteur.moc.content.FlatContentDiff

class MocFileDiff private constructor(
    val diffType: DiffType,
    val flatContentDiff: FlatContentDiff?
) {
    companion object {
        fun new() = MocFileDiff(DiffType.NEW, null)
        fun deleted() = MocFileDiff(DiffType.DELETED, null)
        fun changed(flatContentDiff: FlatContentDiff) = MocFileDiff(DiffType.CHANGED, flatContentDiff)
    }
}
