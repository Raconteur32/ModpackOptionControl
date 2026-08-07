package fr.raconteur.moc.web

// DTOs mirroring the JSON contracts documented in doc/gui-refacto-tech.md §4 and
// doc/gui_refacto/01-shared-types.md. Gson (as configured by ktor-serialization-gson,
// default GsonBuilder) omits null fields rather than emitting explicit `null`.

data class DiffNode(
    val path: String,
    val label: String,
    val kind: String,               // CHANGED | NEW | DELETED
    val oldValue: Any? = null,
    val newValue: Any? = null,
    val hasChildren: Boolean,
    val children: List<DiffNode>,
    val action: String? = null,     // DEFAULT | OVERRIDE | IGNORE | null
    val ignoreKind: String? = null, // SESSION | VALUE | PERMANENT | DIRECTORY
    val unresolved: Boolean = false,
    val source: String? = null
)

data class FileSummary(
    val path: String,
    val kind: String,               // CHANGED | NEW | DELETED
    val stagedCount: Int,
    val hasUnresolved: Boolean = false,
    val ignored: Boolean = false
)

data class DraftEntryDto(
    val filePath: String,
    val optionPath: String,
    val mode: String,               // DEFAULT | OVERRIDE
    val kind: String,                // VALUE | DELETION
    val source: String? = null
)

data class IgnoreEntryDto(
    val filePath: String,
    val optionPath: String,
    val kind: String,               // SESSION | VALUE | PERMANENT
    val targetValue: String? = null
)

data class RecompConflictDto(
    val filePath: String,
    val optionPath: String
)
