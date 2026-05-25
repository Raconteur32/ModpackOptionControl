package fr.raconteur.moc.lua

import fr.raconteur.moc.platform.PlatformService
import java.security.MessageDigest
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

object ScriptUtils {

    fun ensureScripts() {
        val scriptsDir = PlatformService.INSTANCE.getConfigDir().resolve("moc/scripts")

        val scriptList = ScriptUtils::class.java.classLoader
            .getResourceAsStream("scripts/scripts.list")
            ?.bufferedReader()?.readLines()
            ?.filter { it.isNotBlank() }
            ?: return

        for (scriptPath in scriptList) {
            val resourceBytes = ScriptUtils::class.java.classLoader
                .getResourceAsStream("scripts/$scriptPath")
                ?.readBytes()
                ?: continue

            val targetFile = scriptsDir.resolve(scriptPath)

            if (!targetFile.exists() || sha256(targetFile.readBytes()) != sha256(resourceBytes)) {
                targetFile.createParentDirectories()
                targetFile.writeBytes(resourceBytes)
            }
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
