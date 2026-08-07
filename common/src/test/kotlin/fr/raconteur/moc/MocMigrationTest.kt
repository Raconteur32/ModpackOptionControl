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
        val patchDir = configDir.resolve("moc/patches/my-patch").toFile()
        patchDir.mkdirs()
        patchDir.resolve(".mocmeta.json").writeText("{}")
        MocMigration.migrate()
        assertFalse(patchDir.resolve(".mocmeta.json").exists(), "Old .mocmeta.json must be removed")
        assertTrue(patchDir.resolve("mocmeta.json").exists(),   "New mocmeta.json must exist")
    }

    // ── legacy patchs/ directory → patches/ ───────────────────────────────────

    @Test
    fun `legacy patchs directory is renamed to patches with contents intact`() {
        val legacyDir = configDir.resolve("moc/patchs/my-patch").toFile()
        legacyDir.mkdirs()
        legacyDir.resolve("patch.json").writeText("[]")
        MocMigration.migrate()
        assertFalse(configDir.resolve("moc/patchs").toFile().exists(), "Legacy directory must be removed")
        assertTrue(configDir.resolve("moc/patches/my-patch/patch.json").toFile().exists(),
            "Patch folder must survive the rename")
    }

    @Test
    fun `patchs rename is idempotent`() {
        val legacyDir = configDir.resolve("moc/patchs/my-patch").toFile()
        legacyDir.mkdirs()
        legacyDir.resolve("patch.json").writeText("[]")
        MocMigration.migrate()
        assertDoesNotThrow { MocMigration.migrate() }
        assertTrue(configDir.resolve("moc/patches/my-patch/patch.json").toFile().exists())
    }

    @Test
    fun `both patchs and patches present leaves both untouched`() {
        val legacyDir = configDir.resolve("moc/patchs/old-patch").toFile()
        legacyDir.mkdirs()
        legacyDir.resolve("patch.json").writeText("""[{"marker":"legacy"}]""")
        val currentDir = configDir.resolve("moc/patches/new-patch").toFile()
        currentDir.mkdirs()
        currentDir.resolve("patch.json").writeText("""[{"marker":"current"}]""")
        MocMigration.migrate()
        assertTrue(legacyDir.resolve("patch.json").exists(),  "Legacy directory must not be removed when destination exists")
        assertTrue(currentDir.resolve("patch.json").exists(), "Current directory must not be overwritten")
    }
}
