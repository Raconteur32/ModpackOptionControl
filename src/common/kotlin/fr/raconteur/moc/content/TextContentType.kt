package fr.raconteur.moc.content

import de.marhali.json5.Json5Element
import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.filesystem.MocFile

object TextContentType : ContentType() {
    override val id = "text"

    override fun hasPreferredExtension(filename: String) = true

    override fun hasValidContent(file: MocFile) = true

    override fun checkConfidenceScore(file: MocFile) = 3

    override fun getContent(file: MocFile): Json5Element? =
        file.getStringContent()?.let { Json5Primitive.fromString(it) }

    override fun setContent(file: MocFile, content: Json5Element) =
        file.setStringContent(content.asString)
}