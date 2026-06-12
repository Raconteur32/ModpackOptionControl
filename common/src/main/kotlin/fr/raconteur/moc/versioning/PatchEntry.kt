package fr.raconteur.moc.versioning

data class PatchEntry(
    val filePath: String,
    val optionPath: String,
    val fromValue: Any?,
    val toValue: Any?,
    val kind: EntryKind,
    val mode: PatchMode
)
