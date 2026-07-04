package fr.raconteur.moc

import fr.raconteur.moc.test.TestPlatformService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MocMigrationTest {

    private lateinit var platform: TestPlatformService

    @BeforeEach
    fun setUp() {
        platform = TestPlatformService.create()
        platform.installAsPlatformService()
    }

    @AfterEach
    fun tearDown() {
        platform.cleanup()
    }

    private val gameDir    get() = platform.tempDir
    private val configDir  get() = platform.tempDir.resolve("config")

    // ── dot-file names in game dir ────────────────────────────────────────────

    @Test
    fun `dot-prefixed mocmetadata_json is moved to mocfsmetas`() {
        gameDir.resolve(".mocmetadata.json").toFile().writeText("{}")
        MocMigration.migrate()
        assertFalse(gameDir.resolve(".mocmetadata.json").toFile().exists(),         "Source must be removed")
        assertTrue(gameDir.resolve("mocfsmetas/mocmetadata.json").toFile().exists(), "Destination must exist")
    }

    @Test
    fun `dot-prefixed mocappliedpatches_json is moved to mocfsmetas`() {
        gameDir.resolve(".mocappliedpatches.json").toFile().writeText("[]")
        MocMigration.migrate()
        assertFalse(gameDir.resolve(".mocappliedpatches.json").toFile().exists(),         "Source must be removed")
        assertTrue(gameDir.resolve("mocfsmetas/mocappliedpatches.json").toFile().exists(), "Destination must exist")
    }

    // ── flat-root names in game dir ───────────────────────────────────────────

    @Test
    fun `flat-root mocmetadata_json is moved to mocfsmetas`() {
        gameDir.resolve("mocmetadata.json").toFile().writeText("{}")
        MocMigration.migrate()
        assertFalse(gameDir.resolve("mocmetadata.json").toFile().exists(),           "Source must be removed")
        assertTrue(gameDir.resolve("mocfsmetas/mocmetadata.json").toFile().exists(), "Destination must exist")
    }

    @Test
    fun `flat-root mocappliedpatches_json is moved to mocfsmetas`() {
        gameDir.resolve("mocappliedpatches.json").toFile().writeText("[]")
        MocMigration.migrate()
        assertFalse(gameDir.resolve("mocappliedpatches.json").toFile().exists(),           "Source must be removed")
        assertTrue(gameDir.resolve("mocfsmetas/mocappliedpatches.json").toFile().exists(), "Destination must exist")
    }

    // ── log directory ─────────────────────────────────────────────────────────

    @Test
    fun `mocappliedlogs directory is moved to mocfsmetas`() {
        val logsDir = gameDir.resolve("mocappliedlogs").toFile()
        logsDir.mkdirs()
        logsDir.resolve("log.json").writeText("[]")
        MocMigration.migrate()
        assertFalse(logsDir.exists(), "Source directory must be removed")
        assertTrue(gameDir.resolve("mocfsmetas/mocappliedlogs").toFile().isDirectory, "Destination directory must exist")
    }

    // ── idempotency and safety ────────────────────────────────────────────────

    @Test
    fun `already-migrated target file is not overwritten`() {
        val src = gameDir.resolve(".mocmetadata.json").toFile()
        val dst = gameDir.resolve("mocfsmetas/mocmetadata.json").toFile()
        dst.parentFile.mkdirs()
        src.writeText("{\"from\": \"source\"}")
        dst.writeText("{\"from\": \"destination\"}")
        MocMigration.migrate()
        assertTrue("{\"from\": \"destination\"}" in dst.readText(),
            "Pre-existing destination must not be overwritten by migration")
    }

    @Test
    fun `missing source files are silently skipped`() {
        assertDoesNotThrow { MocMigration.migrate() }
    }

    // ── patch .mocmeta.json → mocmeta.json ────────────────────────────────────

    @Test
    fun `patch dot-mocmeta_json is renamed to mocmeta_json`() {
        val patchDir = configDir.resolve("moc/patchs/my-patch").toFile()
        patchDir.mkdirs()
        patchDir.resolve(".mocmeta.json").writeText("{}")
        MocMigration.migrate()
        assertFalse(patchDir.resolve(".mocmeta.json").exists(), "Old .mocmeta.json must be removed")
        assertTrue(patchDir.resolve("mocmeta.json").exists(),   "New mocmeta.json must exist")
    }
}
