package fr.raconteur.moc.web

import fr.raconteur.moc.MocSettings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// pruneRedundant() is called once at server startup (see Main.kt); these tests pin
// the pruning rules themselves against the web IgnoreStore copy.
class IgnoreStorePruneTest : WebTestBase() {

    private val editorFile get() = platform.tempDir.resolve("config/moc/dev/editor.json").toFile()

    @Test
    fun `session and value ignores covered by a permanent ancestor are pruned`() {
        IgnoreStore.add(IgnoreEntry("f.json", "$['a']"), IgnoreKind.PERMANENT)
        IgnoreStore.add(IgnoreEntry("f.json", "$['a']['b']"), IgnoreKind.SESSION)
        IgnoreStore.add(IgnoreEntry("f.json", "$['a']['c']", "1"), IgnoreKind.VALUE)
        IgnoreStore.add(IgnoreEntry("f.json", "$['other']"), IgnoreKind.SESSION)

        IgnoreStore.pruneRedundant()

        assertEquals(1, IgnoreStore.sessionIgnores.size, "covered session entry pruned, other kept")
        assertEquals("$['other']", IgnoreStore.sessionIgnores.single().optionPath)
        assertEquals(0, IgnoreStore.valueIgnores.size, "covered value entry pruned")
        assertEquals(1, IgnoreStore.permanentIgnores.size, "the covering permanent entry stays")
    }

    @Test
    fun `entries under an ignored directory are pruned, recomposition ignores untouched`() {
        MocSettings.addIgnoredPath("dir-x")
        try {
            IgnoreStore.add(IgnoreEntry("dir-x/f.json", "$['a']"), IgnoreKind.SESSION)
            IgnoreStore.add(IgnoreEntry("dir-x/g.json", "$['b']", "2"), IgnoreKind.VALUE)
            IgnoreStore.add(IgnoreEntry("dir-x/h.json", "$['c']"), IgnoreKind.PERMANENT)
            IgnoreStore.add(IgnoreEntry("elsewhere/f.json", "$['a']"), IgnoreKind.SESSION)
            IgnoreStore.addRecomp(IgnoreEntry("dir-x/i.json", "$['d']"))

            IgnoreStore.pruneRedundant()

            assertEquals(0, IgnoreStore.valueIgnores.size)
            assertEquals(0, IgnoreStore.permanentIgnores.size)
            assertEquals(listOf("elsewhere/f.json"), IgnoreStore.sessionIgnores.map { it.filePath })
            assertEquals(1, IgnoreStore.recompositionIgnores.size,
                "recomposition ignores are session-scoped and must not be pruned")
        } finally {
            MocSettings.removeIgnoredPath("dir-x")
        }
    }

    @Test
    fun `no redundant entries means the store file is not rewritten`() {
        IgnoreStore.add(IgnoreEntry("f.json", "$['a']"), IgnoreKind.PERMANENT)
        val before = editorFile.readText()

        IgnoreStore.pruneRedundant()

        assertTrue(IgnoreStore.permanentIgnores.isNotEmpty())
        assertEquals(before, editorFile.readText(), "no change must mean no spurious save")
    }
}
