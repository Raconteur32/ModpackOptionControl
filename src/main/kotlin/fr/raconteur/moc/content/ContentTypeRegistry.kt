package fr.raconteur.moc.content

object ContentTypeRegistry {
    private val registry: MutableList<ContentType> = mutableListOf(JsonContentType)

    fun register(contentType: ContentType) {
        registry.add(contentType)
    }

    fun getAll(): List<ContentType> = registry.toList()

    fun findById(id: String): ContentType? = registry.find { it.getId() == id }
}