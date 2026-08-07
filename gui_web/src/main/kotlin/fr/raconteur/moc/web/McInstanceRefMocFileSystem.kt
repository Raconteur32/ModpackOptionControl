package fr.raconteur.moc.web

import fr.raconteur.moc.MocSettings
import fr.raconteur.moc.filesystem.MocFileSystem
import fr.raconteur.moc.platform.PlatformService
import fr.raconteur.moc.versioning.PatchList
import java.security.MessageDigest

// Web-module copy of the dev reference filesystem (extracted from common; see
// openspec/changes/extract-authoring-from-common). Adds fingerprint-based conditional
// regeneration: the ref tree is only rebuilt at startup when the stored stamp does not
// match the current authoring inputs.
object McInstanceRefMocFileSystem : MocFileSystem(
    rootPath     = PlatformService.INSTANCE.getConfigDir().resolve("moc/dev/ref"),
    ignoredPaths = MocSettings.ignoredPaths,
    hasRef       = true,
    onRefError   = { patchName, e -> PlatformService.INSTANCE.logError("[moc] Failed to rebuild ref for patch '$patchName': ${e.message}", e) }
) {
    private val stampPath
        get() = getRootPath().resolve("mocfsmetas/refstamp.json")

    fun regenerateRefFiles() {
        getRootPath().toFile().walkTopDown()
            .sortedDescending()
            .filter { it != getRootPath().toFile() }
            .forEach { it.delete() }
        reload()

        var failed = false
        applyMultiplePatches(
            PatchList.getAll(),
            forceOverride = true,
            onError = { patchName, e ->
                failed = true
                PlatformService.INSTANCE.logError("[moc] Failed to regenerate ref for patch '$patchName': ${e.message}", e)
            }
        )
        // The stamp is written only after a fully successful regeneration: a crash or a
        // failed patch leaves a missing/stale stamp, forcing regeneration at next start.
        if (!failed) writeStamp(currentFingerprint())
    }

    /** True when no valid stamp exists for the current authoring inputs. */
    fun isStale(): Boolean = readStamp() != currentFingerprint()

    fun regenerateIfStale() {
        if (isStale()) regenerateRefFiles()
    }

    internal fun currentFingerprint(appVersion: String = currentAppVersion()): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val configDir = PlatformService.INSTANCE.getConfigDir()

        // Patch application order matters — hash the list file verbatim.
        configDir.resolve("moc/patch-list.json").toFile()
            .let { if (it.exists()) digest.update(it.readBytes()) }

        val patchesRoot = configDir.resolve("moc/${PatchList.PATCHES_DIR_NAME}").toFile()
        if (patchesRoot.isDirectory) {
            patchesRoot.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.relativeTo(patchesRoot).invariantSeparatorsPath }
                .forEach {
                    digest.update(it.relativeTo(patchesRoot).invariantSeparatorsPath.toByteArray())
                    digest.update(0)
                    digest.update(it.readBytes())
                    digest.update(0)
                }
        }

        // Ignored paths affect what the ref tree contains.
        digest.update(MocSettings.ignoredPaths.map { it.toString() }.sorted().joinToString("\n").toByteArray())
        digest.update(0)
        // Application logic may change between releases.
        digest.update(appVersion.toByteArray())

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun currentAppVersion(): String =
        McInstanceRefMocFileSystem::class.java.`package`?.implementationVersion ?: "dev"

    private fun readStamp(): String? = try {
        stampPath.toFile().takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }
    } catch (_: Exception) { null }

    private fun writeStamp(fingerprint: String) {
        stampPath.toFile().apply { parentFile.mkdirs(); writeText(fingerprint) }
    }
}
