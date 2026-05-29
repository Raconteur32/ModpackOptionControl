package fr.raconteur.moc.filesystem

import com.google.gson.JsonElement
import com.ibm.icu.text.CharsetDetector
import fr.raconteur.moc.content.ContentType
import fr.raconteur.moc.content.ContentTypeRegistry
import fr.raconteur.moc.content.FlatContent
import fr.raconteur.moc.content.FlatContentDiff
import fr.raconteur.moc.content.TextContentType
import fr.raconteur.moc.lua.DiffLuaContext
import fr.raconteur.moc.lua.api.MocFileAPIWrapper
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class MocFile private constructor(
    val fileSystem: MocFileSystem,
    val relativePath: Path,
    val encoding: String,
    val contentType: ContentType,
    val diffAlg: String,
    val exists: Boolean
) {
    companion object {
        const val DEFAULT_DIFF_ALG = "default/diff"

        fun load(fileSystem: MocFileSystem, relativePath: Path): MocFile {
            val absPath = fileSystem.getRootPath().resolve(relativePath)
            if (!absPath.toFile().exists()) throw RuntimeException("File does not exist: $absPath")

            val metadata = fileSystem.getFileMetadata(relativePath)
            val encoding: String
            val contentType: ContentType
            val diffAlg: String

            if (metadata != null) {
                encoding    = metadata["encoding"] ?: StandardCharsets.UTF_8.name()
                contentType = metadata["content"]?.let { ContentTypeRegistry.findById(it) } ?: TextContentType
                diffAlg     = metadata["diffalg"] ?: DEFAULT_DIFF_ALG
            } else {
                encoding = detectEncoding(absPath)
                val probe = MocFile(fileSystem, relativePath, encoding, TextContentType, DEFAULT_DIFF_ALG, exists = true)
                contentType = inferContentType(probe)
                diffAlg = DEFAULT_DIFF_ALG
            }

            val file = MocFile(fileSystem, relativePath, encoding, contentType, diffAlg, exists = true)
            fileSystem.register(file)
            return file
        }

        fun ensureWritable(
            fileSystem: MocFileSystem,
            relativePath: Path,
            encoding: String,
            contentType: ContentType,
            diffAlg: String
        ): MocFile {
            val exists = fileSystem.getRootPath().resolve(relativePath).toFile().exists()
            val file = MocFile(fileSystem, relativePath, encoding, contentType, diffAlg, exists)
            fileSystem.register(file)
            return file
        }

        fun ghost(
            fileSystem: MocFileSystem,
            relativePath: Path,
            encoding: String,
            contentType: ContentType,
            diffAlg: String
        ): MocFile {
            val exists = fileSystem.getRootPath().resolve(relativePath).toFile().exists()
            return MocFile(fileSystem, relativePath, encoding, contentType, diffAlg, exists)
        }

        fun isBinary(path: Path): Boolean {
            val buffer = ByteArray(8000)
            val read = Files.newInputStream(path).use { it.read(buffer) }
            if (read <= 0) return false
            return buffer.take(read).any { it == 0.toByte() }
        }

        private fun inferContentType(probe: MocFile): ContentType {
            var bestType: ContentType? = null
            var bestScore = 0
            for (type in ContentTypeRegistry.getAll()) {
                val score = type.checkConfidenceScore(probe)
                if (score > bestScore) {
                    bestType = type
                    bestScore = score
                }
            }
            return if (bestScore == 0) TextContentType else bestType!!
        }

        private fun detectEncoding(path: Path): String {
            if (isBinary(path)) throw IllegalArgumentException("File appears to be binary: $path")
            detectBOM(path)?.let { return it }
            val data = Files.readAllBytes(path)
            val detector = CharsetDetector()
            detector.setText(data)
            val match = detector.detect()
            if (match != null && match.confidence >= 50) return match.name
            try {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data))
                return StandardCharsets.UTF_8.name()
            } catch (_: Exception) {}
            return Charset.defaultCharset().name()
        }

        private fun detectBOM(path: Path): String? {
            val bytes = Files.newInputStream(path).use { it.readNBytes(4) }
            return when {
                bytes.size >= 3
                        && bytes[0] == 0xEF.toByte()
                        && bytes[1] == 0xBB.toByte()
                        && bytes[2] == 0xBF.toByte() -> "UTF-8"
                bytes.size >= 4
                        && bytes[0] == 0x00.toByte()
                        && bytes[1] == 0x00.toByte()
                        && bytes[2] == 0xFE.toByte()
                        && bytes[3] == 0xFF.toByte() -> "UTF-32BE"
                bytes.size >= 4
                        && bytes[0] == 0xFF.toByte()
                        && bytes[1] == 0xFE.toByte()
                        && bytes[2] == 0x00.toByte()
                        && bytes[3] == 0x00.toByte() -> "UTF-32LE"
                bytes.size >= 2
                        && bytes[0] == 0xFE.toByte()
                        && bytes[1] == 0xFF.toByte() -> "UTF-16BE"
                bytes.size >= 2
                        && bytes[0] == 0xFF.toByte()
                        && bytes[1] == 0xFE.toByte() -> "UTF-16LE"
                else -> null
            }
        }
    }

    fun getAbsolutePath(): Path = fileSystem.getRootPath().resolve(relativePath)
    fun getFileName(): String = relativePath.fileName.toString()

    fun getStringContent(): String? {
        if (!exists) return null
        return getAbsolutePath().toFile().readText(Charset.forName(encoding))
    }

    fun setStringContent(text: String) {
        getAbsolutePath().toFile().parentFile?.mkdirs()
        getAbsolutePath().toFile().writeText(text, Charset.forName(encoding))
    }

    fun getContent(): JsonElement? {
        if (!exists) return null
        return contentType.getContent(this)
    }

    fun setContent(content: JsonElement) = contentType.setContent(this, content)

    fun getFlatContent(): FlatContent? {
        if (!exists) return null
        return contentType.getFlatContent(this)
    }

    fun diffFrom(other: MocFile): FlatContentDiff {
        val ctx = DiffLuaContext(diffAlg)
        return ctx.diff(MocFileAPIWrapper(other), MocFileAPIWrapper(this), relativePath.toString())
    }
}
