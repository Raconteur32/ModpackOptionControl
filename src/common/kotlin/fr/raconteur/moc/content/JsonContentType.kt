package fr.raconteur.moc.content

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.google.gson.Strictness
import fr.raconteur.moc.filesystem.MocFile

object JsonContentType : ContentType() {
    override val id = "json"

    private val extensions = listOf(".json", ".json5")
    private val gson = GsonBuilder().setStrictness(Strictness.LENIENT).create()
    private val gsonWriter = GsonBuilder().setPrettyPrinting().create()

    override fun hasPreferredExtension(filename: String): Boolean =
        extensions.any { filename.endsWith(it) }

    override fun hasValidContent(file: MocFile): Boolean {
        val content = file.getStringContent()
        if (content.isBlank()) return false
        return try {
            gson.fromJson(content, JsonElement::class.java) != null
        } catch (_: JsonSyntaxException) {
            false
        }
    }

    override fun getContent(file: MocFile): JsonElement {
        val content = file.getStringContent()
        return gson.fromJson(content, JsonElement::class.java)
    }

    override fun setContent(file: MocFile, content: JsonElement) =
        file.setStringContent(gsonWriter.toJson(content))
}