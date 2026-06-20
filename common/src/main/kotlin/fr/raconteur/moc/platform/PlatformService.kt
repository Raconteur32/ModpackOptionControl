package fr.raconteur.moc.platform

import java.nio.file.Path

interface PlatformService {
    fun getPlatformName(): String
    fun getGameDir(): Path
    fun getConfigDir(): Path
    fun logInfo(message: String)
    fun logError(message: String, e: Exception? = null)
    fun logCritical(message: String, e: Exception? = null)

    companion object {
        lateinit var INSTANCE: PlatformService
    }
}
