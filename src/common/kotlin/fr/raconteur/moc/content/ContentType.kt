package fr.raconteur.moc.content

import com.google.gson.JsonElement
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.GsonJsonProvider
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider
import fr.raconteur.moc.filesystem.MocFile

abstract class ContentType {
    abstract val id: String

    abstract fun hasPreferredExtension(filename: String): Boolean

    abstract fun hasValidContent(file: MocFile): Boolean

    abstract fun getContent(file: MocFile): JsonElement?

    abstract fun setContent(file: MocFile, content: JsonElement)

    open fun checkConfidenceScore(file: MocFile): Int {
        if (!hasValidContent(file)) return 0
        var score = 1
        if (hasPreferredExtension(file.getFileName())) score += 2
        return score
    }

    fun getFlatContent(file: MocFile): FlatContent? {
        val element = getContent(file) ?: return null
        val pathConfig = Configuration.builder()
            .jsonProvider(GsonJsonProvider())
            .mappingProvider(GsonMappingProvider())
            .options(Option.AS_PATH_LIST, Option.SUPPRESS_EXCEPTIONS)
            .build()
        val valueConfig = Configuration.builder()
            .jsonProvider(GsonJsonProvider())
            .mappingProvider(GsonMappingProvider())
            .build()
        val pathsRaw: com.google.gson.JsonArray? = JsonPath.using(pathConfig).parse(element).read("$..*")
        val paths: List<String> = pathsRaw?.map { it.asString } ?: emptyList()
        val ctx = JsonPath.using(valueConfig).parse(element)
        return FlatContent((listOf("$") + paths).associate { path -> path to ctx.read<Any?>(path) })
    }
}