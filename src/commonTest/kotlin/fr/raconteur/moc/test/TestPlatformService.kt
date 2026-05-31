package fr.raconteur.moc.test

import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Files
import java.nio.file.Path

class TestPlatformService private constructor(val tempDir: Path) : PlatformService {

    override fun getPlatformName() = "Test"
    override fun getGameDir(): Path = tempDir
    override fun getConfigDir(): Path = tempDir.resolve("config")

    fun installAsPlatformService() {
        PlatformService.INSTANCE = this
    }

    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    companion object {
        fun create(): TestPlatformService {
            val dir = Files.createTempDirectory("moc-test-")
            return TestPlatformService(dir)
        }
    }
}
