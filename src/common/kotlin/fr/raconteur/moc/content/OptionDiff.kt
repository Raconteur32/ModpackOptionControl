package fr.raconteur.moc.content

sealed class OptionDiff {
    abstract val filePath: String
    abstract val path: String
    abstract val oldValue: Any?
    abstract val newValue: Any?

    class New(
        override val filePath: String,
        override val path: String,
        override val newValue: Any?
    ) : OptionDiff() {
        override val oldValue: Any? = null
    }

    class Deleted(
        override val filePath: String,
        override val path: String,
        override val oldValue: Any?
    ) : OptionDiff() {
        override val newValue: Any? = null
    }

    class Changed(
        override val filePath: String,
        override val path: String,
        override val oldValue: Any?,
        override val newValue: Any?
    ) : OptionDiff()
}
