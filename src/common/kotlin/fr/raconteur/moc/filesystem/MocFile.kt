package fr.raconteur.moc.filesystem

import com.ibm.icu.text.CharsetDetector
import de.marhali.json5.Json5Array
import de.marhali.json5.Json5Element
import de.marhali.json5.Json5Object
import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.content.ContentType
import fr.raconteur.moc.content.ContentTypeRegistry
import fr.raconteur.moc.content.FlatContent
import fr.raconteur.moc.content.FlatContentDiff
import fr.raconteur.moc.content.TextContentType
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class MocFile private constructor(
    val fileSystem: MocFileSystem,
    val relativePath: Path,
    val exists: Boolean,
    val metadata: MutableMap<String, String>
) {
    val encoding: String
        get() = metadata["encoding"] ?: StandardCharsets.UTF_8.name()
    val contentType: ContentType
        get() = metadata["content"]?.let { ContentTypeRegistry.findById(it) } ?: TextContentType

    fun initContentTypeSpecificMetadata() {
        metadata.putAll(contentType.getSpecificMetadata(this))
    }

    companion object {
        fun load(fileSystem: MocFileSystem, relativePath: Path): MocFile {
            val absPath = fileSystem.getRootPath().resolve(relativePath)
            if (!absPath.toFile().exists()) throw RuntimeException("File does not exist: $absPath")
            val meta = fileSystem.getFileMetadata(relativePath)?.toMutableMap() ?: mutableMapOf()
            if (!meta.containsKey("encoding")) meta["encoding"] = detectEncoding(absPath)
            if (!meta.containsKey("content")) {
                val probe = MocFile(fileSystem, relativePath, exists = true, metadata = meta.toMutableMap())
                meta["content"] = inferContentType(probe).id
            }
            val file = MocFile(fileSystem, relativePath, exists = true, metadata = meta)
            file.initContentTypeSpecificMetadata()
            fileSystem.register(file)
            return file
        }

        fun ensureWritable(
            fileSystem: MocFileSystem,
            relativePath: Path,
            contentTypeId: String,
            metadata: Map<String, String> = emptyMap()
        ): MocFile {
            val meta = metadata.toMutableMap().also { it["content"] = contentTypeId }
            val exists = fileSystem.getRootPath().resolve(relativePath).toFile().exists()
            val file = MocFile(fileSystem, relativePath, exists, meta)
            file.initContentTypeSpecificMetadata()
            fileSystem.register(file)
            return file
        }

        fun ghost(
            fileSystem: MocFileSystem,
            relativePath: Path,
            contentTypeId: String,
            metadata: Map<String, String> = emptyMap()
        ): MocFile {
            val meta = metadata.toMutableMap().also { it["content"] = contentTypeId }
            val exists = fileSystem.getRootPath().resolve(relativePath).toFile().exists()
            val file = MocFile(fileSystem, relativePath, exists, meta)
            file.initContentTypeSpecificMetadata()
            return file
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

    fun getContent(): Json5Element? {
        if (!exists) return null
        return contentType.getContent(this)
    }

    fun setContent(content: Json5Element) = contentType.setContent(this, content)

    fun getFlatContent(): FlatContent? {
        if (!exists) return null
        return contentType.getFlatContent(this)
    }

    fun diffFrom(other: MocFile): FlatContentDiff {
        val diff = FlatContentDiff(relativePath.toString())
        val fromFlat = other.getFlatContent() ?: FlatContent(emptyMap())
        val toFlat = getFlatContent()

        if (toFlat == null) {
            diff.addDeleted("", fromFlat["$"])
            return diff
        }

        for (path in fromFlat.keys.sorted()) {
            if (!toFlat.containsKey(path)) diff.addDeleted(path, fromFlat[path])
        }

        for (path in toFlat.keys.sortedDescending()) {
            val toVal = toFlat[path]
            if (!fromFlat.containsKey(path)) {
                diff.addNew(path, toVal)
                if (toVal is Json5Array) diff.cutBranch(path)
            } else {
                val fromVal = fromFlat[path]
                when {
                    toVal is Json5Object || toVal is Json5Array -> {
                        if (diff.hasLeaf(path)) {
                            diff.addChanged(path, fromVal, toVal)
                            if (toVal is Json5Array) diff.cutBranch(path)
                        }
                    }
                    !jsonValuesEqual(fromVal, toVal) -> diff.addChanged(path, fromVal, toVal)
                }
            }
        }

        diff.rationalize()
        return diff
    }

    private fun jsonValuesEqual(a: Any?, b: Any?): Boolean {
        if (a === b) return true
        if (a is Json5Primitive && b is Json5Primitive) {
            if (a.isNumber && b.isNumber) return a.asString == b.asString
            return a == b
        }
        return a == b
    }
}
