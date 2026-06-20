package fr.raconteur.moc.filesystem

import fr.raconteur.moc.content.ContentType
import fr.raconteur.moc.content.ContentTypeRegistry
import fr.raconteur.moc.content.PropertiesContentType
import fr.raconteur.moc.content.TextContentType
import org.mozilla.universalchardet.UniversalDetector
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal object MocFileInspector {

    private val fileNameOverrides: Map<String, ContentType> = mapOf(
        "options.txt" to PropertiesContentType
    )

    fun isBinary(path: Path): Boolean {
        val buffer = ByteArray(8000)
        val read = Files.newInputStream(path).use { it.read(buffer) }
        if (read <= 0) return false
        return buffer.take(read).any { it == 0.toByte() }
    }

    fun detectEncoding(path: Path): String {
        if (isBinary(path)) throw IllegalArgumentException("File appears to be binary: $path")
        detectBOM(path)?.let { return it }
        val data = Files.readAllBytes(path)
        val detected = UniversalDetector.detectCharset(path)
        if (detected != null) return detected
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data))
            return StandardCharsets.UTF_8.name()
        } catch (_: Exception) {}
        return Charset.defaultCharset().name()
    }

    fun inferContentType(probe: MocFile): ContentType {
        fileNameOverrides[probe.getFileName()]?.let { return it }
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
