package fr.raconteur.moc.content

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ContentTypeRegistryTest {

    @Test
    fun `getAll includes JSON, Properties, and TOML by default`() {
        val ids = ContentTypeRegistry.getAll().map { it.id }
        assertTrue("json"       in ids, "Expected 'json' in registry")
        assertTrue("properties" in ids, "Expected 'properties' in registry")
        assertTrue("toml"       in ids, "Expected 'toml' in registry")
    }

    @Test
    fun `findById returns the correct type for each known id`() {
        assertEquals("json",       ContentTypeRegistry.findById("json")?.id)
        assertEquals("properties", ContentTypeRegistry.findById("properties")?.id)
        assertEquals("toml",       ContentTypeRegistry.findById("toml")?.id)
    }

    @Test
    fun `findById returns null for an unknown id`() {
        assertNull(ContentTypeRegistry.findById("unknown-format-xyz"))
    }

    @Test
    fun `TextContentType is not in the registry (it is the fallback, not a registered type)`() {
        assertNull(ContentTypeRegistry.findById("text"),
            "TextContentType must not be registered — it is the implicit fallback")
    }
}
