package fr.raconteur.moc.filesystem

import fr.raconteur.moc.content.FlatContentDiff

enum class FileDiffKind { NEW, DELETED, CHANGED }

class MocFileDiff private constructor(
    val kind: FileDiffKind,
    val flatContentDiff: FlatContentDiff
) {
    companion object {
        fun new(flatContentDiff: FlatContentDiff)     = MocFileDiff(FileDiffKind.NEW, flatContentDiff)
        fun deleted(flatContentDiff: FlatContentDiff) = MocFileDiff(FileDiffKind.DELETED, flatContentDiff)
        fun changed(flatContentDiff: FlatContentDiff) = MocFileDiff(FileDiffKind.CHANGED, flatContentDiff)
    }
}
