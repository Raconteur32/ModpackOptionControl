package fr.raconteur.moc.content

import de.marhali.json5.Json5Element
import fr.raconteur.moc.filesystem.MocFile

abstract class ContentType {
    abstract val id: String

    abstract fun hasPreferredExtension(filename: String): Boolean

    abstract fun hasValidContent(file: MocFile): Boolean

    abstract fun getContent(file: MocFile): Json5Element?

    abstract fun setContent(file: MocFile, content: Json5Element)

    open fun getSpecificMetadata(file: MocFile): Map<String, String> = emptyMap()

    open fun checkConfidenceScore(file: MocFile): Int {
        if (!hasValidContent(file)) return 0
        var score = 1
        if (hasPreferredExtension(file.getFileName())) score += 2
        return score
    }

    fun getFlatContent(file: MocFile): FlatContent? {
        val root = getContent(file) ?: return null
        val entries = mutableMapOf<String, Any?>()
        flattenInto("$", root, entries)
        return FlatContent(entries)
    }

    private fun flattenInto(path: String, element: Json5Element, out: MutableMap<String, Any?>) {
        out[path] = element
        when {
            element.isJson5Object -> for ((key, value) in element.asJson5Object.entrySet())
                flattenInto("$path['$key']", value, out)
            element.isJson5Array -> {
                val arr = element.asJson5Array
                for (i in 0 until arr.size())
                    flattenInto("$path[$i]", arr.get(i), out)
            }
        }
    }
}