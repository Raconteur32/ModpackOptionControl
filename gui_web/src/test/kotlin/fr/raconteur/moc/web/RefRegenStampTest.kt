package fr.raconteur.moc.web

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RefRegenStampTest : WebTestBase() {

    private val stampFile get() = platform.tempDir.resolve("config/moc/dev/ref/mocfsmetas/refstamp.json").toFile()
    private val patchListFile get() = platform.tempDir.resolve("config/moc/patch-list.json").toFile()
    private val patchesDir get() = platform.tempDir.resolve("config/moc/patches").toFile()

    private fun writePatch(name: String, entriesJson: String = "[]") {
        patchesDir.resolve(name).apply { mkdirs() }.resolve("patch.json").writeText(entriesJson)
    }

    @Test
    fun `missing stamp means the ref is stale`() {
        assertFalse(stampFile.exists(), "precondition: no stamp after state reset")
        assertTrue(McInstanceRefMocFileSystem.isStale())
    }

    @Test
    fun `regeneration writes the stamp and clears staleness`() {
        McInstanceRefMocFileSystem.regenerateRefFiles()
        assertTrue(stampFile.exists(), "regeneration must write the stamp")
        assertFalse(McInstanceRefMocFileSystem.isStale())
    }

    @Test
    fun `editing a patch file makes the ref stale`() {
        writePatch("p1", """[]""")
        patchListFile.apply { parentFile.mkdirs() }.writeText("""["p1"]""")
        McInstanceRefMocFileSystem.regenerateRefFiles()
        assertFalse(McInstanceRefMocFileSystem.isStale())

        writePatch("p1", """[{"file_path":"x.json","option_path":"$","from_value":null,"to_value":{},"kind":"VALUE","mode":"OVERRIDE"}]""")
        assertTrue(McInstanceRefMocFileSystem.isStale(), "out-of-band patch edit must be detected")
    }

    @Test
    fun `reordering the patch list makes the ref stale`() {
        writePatch("p1"); writePatch("p2")
        patchListFile.apply { parentFile.mkdirs() }.writeText("""["p1","p2"]""")
        McInstanceRefMocFileSystem.regenerateRefFiles()
        assertFalse(McInstanceRefMocFileSystem.isStale())

        patchListFile.writeText("""["p2","p1"]""")
        assertTrue(McInstanceRefMocFileSystem.isStale(), "application order change must be detected")
    }

    @Test
    fun `app version participates in the fingerprint`() {
        assertNotEquals(
            McInstanceRefMocFileSystem.currentFingerprint("1.0"),
            McInstanceRefMocFileSystem.currentFingerprint("2.0")
        )
    }

    @Test
    fun `deleting the stamp (crash mid-regen) forces regeneration`() {
        McInstanceRefMocFileSystem.regenerateRefFiles()
        assertFalse(McInstanceRefMocFileSystem.isStale())
        stampFile.delete()
        assertTrue(McInstanceRefMocFileSystem.isStale())
    }
}
