package fr.raconteur.moc.versioning

import fr.raconteur.moc.test.TestPlatformService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class PatchListDeletionTest {

    private lateinit var platform: TestPlatformService

    @BeforeEach
    fun setup() {
        platform = TestPlatformService.create()
        platform.installAsPlatformService()
    }

    @AfterEach
    fun cleanup() {
        platform.cleanup()
    }

    private fun createFakePatch(name: String) {
        val dir = platform.getConfigDir().resolve("moc/patchs/$name").toFile()
        dir.mkdirs()
        dir.resolve("patch.json").writeText("[]")
        PatchList.add(name)
    }

    @Test
    fun `delete removes patch from active list`() {
        createFakePatch("p1")
        createFakePatch("p2")
        PatchList.delete("p1")
        assertFalse(PatchList.getAll().contains("p1"))
        assertTrue(PatchList.getAll().contains("p2"))
    }

    @Test
    fun `delete adds patch to deleted list`() {
        createFakePatch("p1")
        PatchList.delete("p1")
        assertTrue(PatchList.getAllDeleted().contains("p1"))
    }

    @Test
    fun `delete removes patch folder from disk`() {
        createFakePatch("p1")
        val dir = platform.getConfigDir().resolve("moc/patchs/p1").toFile()
        assertTrue(dir.exists())
        PatchList.delete("p1")
        assertFalse(dir.exists())
    }

    @Test
    fun `runStartupCleanup deletes folders listed in deleted-patch-list`() {
        // Simulate a patch that was deleted on the server side:
        // folder exists on disk but patch was added to deleted list (not in active list)
        val dir = platform.getConfigDir().resolve("moc/patchs/orphan").toFile()
        dir.mkdirs()
        dir.resolve("patch.json").writeText("[]")
        PatchList.addToDeleted("orphan")

        assertTrue(dir.exists())
        PatchList.runStartupCleanup()
        assertFalse(dir.exists())
    }

    @Test
    fun `add removes patch name from deleted list`() {
        // Patch was previously deleted
        PatchList.addToDeleted("reused")
        assertTrue(PatchList.getAllDeleted().contains("reused"))

        // Now re-create it with the same name
        createFakePatch("reused")
        assertFalse(PatchList.getAllDeleted().contains("reused"))
        assertTrue(PatchList.getAll().contains("reused"))
    }

    @Test
    fun `delete preserves other patches in active list order`() {
        createFakePatch("a")
        createFakePatch("b")
        createFakePatch("c")
        PatchList.delete("b")
        assertEquals(listOf("a", "c"), PatchList.getAll())
    }

    @Test
    fun `setAll replaces entire active list`() {
        createFakePatch("x")
        createFakePatch("y")
        PatchList.setAll(listOf("y", "x"))
        assertEquals(listOf("y", "x"), PatchList.getAll())
    }
}
