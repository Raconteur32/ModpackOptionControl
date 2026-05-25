package fr.raconteur.moc.content

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import fr.raconteur.moc.filesystem.MocFile

object TextContentType : ContentType() {
    override val id = "text"

    override fun hasPreferredExtension(filename: String) = true

    override fun hasValidContent(file: MocFile) = true

    override fun checkConfidenceScore(file: MocFile) = 3

    override fun getContent(file: MocFile): JsonElement = JsonPrimitive(file.getStringContent())

    override fun setContent(file: MocFile, content: JsonElement) =
        file.setStringContent(content.asString)
}