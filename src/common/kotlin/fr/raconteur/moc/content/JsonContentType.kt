package fr.raconteur.moc.content

import de.marhali.json5.Json5
import de.marhali.json5.Json5Element
import fr.raconteur.moc.filesystem.MocFile

object JsonContentType : ContentType() {
    override val id = "json"

    private val extensions = listOf(".json", ".json5")

    private val reader: Json5 = Json5.builder { it.quoteSingle().quoteless().parseComments().allowNaN().allowInfinity().build() }
    private val writer: Json5 = Json5.builder { it.prettyPrinting().build() }

    override fun hasPreferredExtension(filename: String): Boolean =
        extensions.any { filename.endsWith(it) }

    override fun hasValidContent(file: MocFile): Boolean {
        val content = file.getStringContent() ?: return false
        if (content.isBlank()) return false
        return try {
            reader.parse(content) != null
        } catch (_: Exception) {
            false
        }
    }

    override fun getContent(file: MocFile): Json5Element? {
        val content = file.getStringContent() ?: return null
        return try { reader.parse(content) } catch (_: Exception) { null }
    }

    override fun setContent(file: MocFile, content: Json5Element) =
        file.setStringContent(writer.serialize(content))
}