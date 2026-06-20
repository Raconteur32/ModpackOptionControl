package fr.raconteur.moc.test

import fr.raconteur.moc.platform.PlatformService
import java.nio.file.Files
import java.nio.file.Path

class TestPlatformService private constructor(val tempDir: Path) : PlatformService {

    override fun getPlatformName() = "Test"
    override fun getGameDir(): Path = tempDir
    override fun getConfigDir(): Path = tempDir.resolve("config")
    override fun logInfo(message: String) = println("[MOC-TEST] $message")
    override fun logError(message: String, e: Exception?) { System.err.println("[MOC-TEST][ERROR] $message"); e?.printStackTrace(System.err) }
    override fun logCritical(message: String, e: Exception?) { System.err.println("[MOC-TEST][CRITICAL] $message"); e?.printStackTrace(System.err) }

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
