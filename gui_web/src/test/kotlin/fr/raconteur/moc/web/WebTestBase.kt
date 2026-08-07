package fr.raconteur.moc.web

import fr.raconteur.moc.filesystem.McInstanceMocFileSystem
import fr.raconteur.moc.test.TestPlatformService
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class WebTestBase {

    protected val platform = TestPlatformService.create()

    @BeforeAll
    fun initAll() {
        platform.installAsPlatformService()
        McInstanceMocFileSystem.files
        McInstanceRefMocFileSystem.files
        DraftPatch.entries
    }

    @BeforeEach
    fun resetState() {
        platform.tempDir.toFile()
            .listFiles { _, n -> n.endsWith(".json") || n.endsWith(".json5") }
            ?.forEach { it.delete() }
        platform.tempDir.resolve("config/moc/dev/ref").toFile()
            .apply { deleteRecursively(); mkdirs() }
        platform.tempDir.resolve("config/moc/patches").toFile().deleteRecursively()
        platform.tempDir.resolve("config/moc/patch-list.json").toFile().delete()
        platform.tempDir.resolve("config/moc/deleted-patch-list.json").toFile().delete()
        platform.tempDir.resolve("config/moc/dev/editor.json").toFile().delete()
        DraftPatch.clear()
        RecompositionDraft.clear()
        IgnoreStore.reset()
        McInstanceMocFileSystem.reload()
        McInstanceRefMocFileSystem.reload()
    }

    @AfterAll
    fun cleanup() { platform.cleanup() }

    protected fun gameFile(name: String, content: String) =
        platform.tempDir.resolve(name).toFile()
            .also { it.parentFile.mkdirs() }.writeText(content)

    protected fun refFile(name: String, content: String) {
        platform.tempDir.resolve("config/moc/dev/ref/$name").toFile()
            .also { it.parentFile.mkdirs() }.writeText(content)
        McInstanceRefMocFileSystem.reload()
    }

    protected fun webTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { mocModule() }
            val client = createClient { install(ContentNegotiation) { gson() } }
            block(client)
        }
}
