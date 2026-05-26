package fr.raconteur.moc.content

sealed class DiffType {
    abstract val path: String
    abstract val oldValue: Any?
    abstract val newValue: Any?

    class New(override val path: String, override val newValue: Any?) : DiffType() {
        override val oldValue: Any? = null
    }

    class Deleted(override val path: String, override val oldValue: Any?) : DiffType() {
        override val newValue: Any? = null
    }

    class Changed(
        override val path: String,
        override val oldValue: Any?,
        override val newValue: Any?
    ) : DiffType()
}
