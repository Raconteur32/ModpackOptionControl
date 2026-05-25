package fr.raconteur.moc.platform

import java.nio.file.Path

interface PlatformService {
    fun getPlatformName(): String
    fun getGameDir(): Path
    fun getConfigDir(): Path

    companion object {
        lateinit var INSTANCE: PlatformService
    }
}
