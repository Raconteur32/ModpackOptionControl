package fr.raconteur.moc.filesystem

import de.marhali.json5.Json5Array
import de.marhali.json5.Json5Element
import de.marhali.json5.Json5Object
import de.marhali.json5.Json5Primitive
import fr.raconteur.moc.content.ContentType
import fr.raconteur.moc.content.ContentTypeRegistry
import fr.raconteur.moc.content.FlatContent
import fr.raconteur.moc.content.FlatContentDiff
import fr.raconteur.moc.content.TextContentType
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class MocFile private constructor(
    val fileSystem: MocFileSystem,
    val relativePath: Path,
    var exists: Boolean,
    val metadata: MutableMap<String, String>
) {
    val encoding: String
        get() = metadata["encoding"] ?: StandardCharsets.UTF_8.name()
    val contentType: ContentType
        get() = metadata["content"]?.let { ContentTypeRegistry.findById(it) } ?: TextContentType

    fun ensureContentTypeSpecificMetadata() {
        contentType.getSpecificMetadata(this).forEach { (k, v) -> metadata.putIfAbsent(k, v) }
    }

    companion object {
        fun load(fileSystem: MocFileSystem, relativePath: Path): MocFile {
            val absPath = fileSystem.getRootPath().resolve(relativePath)
            if (!absPath.toFile().exists()) throw RuntimeException("File does not exist: $absPath")
            val meta = fileSystem.getFileMetadata(relativePath)?.toMutableMap() ?: mutableMapOf()
            if (!meta.containsKey("encoding")) meta["encoding"] = MocFileInspector.detectEncoding(absPath)
            if (!meta.containsKey("content")) {
                val probe = MocFile(fileSystem, relativePath, exists = true, metadata = meta.toMutableMap())
                meta["content"] = MocFileInspector.inferContentType(probe).id
            }
            val file = MocFile(fileSystem, relativePath, exists = true, metadata = meta)
            file.ensureContentTypeSpecificMetadata()
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
            file.ensureContentTypeSpecificMetadata()
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
            file.ensureContentTypeSpecificMetadata()
            return file
        }
    }

    fun getAbsolutePath(): Path = fileSystem.getRootPath().resolve(relativePath)
    fun getFileName(): String = relativePath.fileName.toString()

    fun getStringContent(): String? {
        if (!exists) return null
        return getAbsolutePath().toFile().readText(Charset.forName(encoding))
    }

    fun setStringContent(text: String) {
        val absPath = getAbsolutePath()
        absPath.toFile().parentFile?.mkdirs()
        val bytes = text.toByteArray(Charset.forName(encoding))
        FileOutputStream(absPath.toFile()).use { fos ->
            fos.write(bytes)
            fos.flush()
            fos.fd.sync()
        }
        exists = true
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
