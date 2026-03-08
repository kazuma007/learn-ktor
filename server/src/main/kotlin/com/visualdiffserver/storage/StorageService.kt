package com.visualdiffserver.storage

import com.visualdiffserver.config.AppConfig
import com.visualdiffserver.domain.ArtifactKind
import io.ktor.http.ContentType
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.io.path.Path as toPath
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.name

class StorageService(private val config: AppConfig) {
    init {
        config.assetsDir.createDirectories()
        config.runsDir.createDirectories()
    }

    data class StoredFile(
        val filename: String,
        val contentType: String,
        val byteSize: Long,
        val sha256: String,
        val storagePath: String,
    )

    data class ScannedArtifact(
        val kind: ArtifactKind,
        val filename: String,
        val contentType: String,
        val byteSize: Long,
        val storagePath: String,
    )

    fun storeAsset(
        originalFilename: String,
        contentType: String,
        data: InputStream,
    ): StoredFile {
        val safeName = sanitizeFilename(originalFilename)
        val targetName = "${UUID.randomUUID()}_$safeName"
        val target = config.assetsDir.resolve(targetName)

        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L

        data.use { input ->
            Files.newOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    totalBytes += read
                }
            }
        }

        return StoredFile(
            filename = safeName,
            contentType = contentType,
            byteSize = totalBytes,
            sha256 = digest.digest().toHex(),
            storagePath = target.toAbsolutePath().toString(),
        )
    }

    fun ensureRunOutputDir(runId: UUID): Path {
        val outputDir = config.runsDir.resolve(runId.toString())
        outputDir.createDirectories()
        return outputDir
    }

    fun scanArtifacts(outputDir: Path): List<ScannedArtifact> {
        if (!outputDir.exists()) return emptyList()

        val artifacts = mutableListOf<ScannedArtifact>()
        return Files.walk(outputDir).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { file ->
                val artifact = toArtifact(file)
                if (artifact != null) {
                    artifacts.add(artifact)
                }
            }
            artifacts
        }
    }

    fun readFile(path: String): Path {
        return toPath(path)
    }

    private fun toArtifact(file: Path): ScannedArtifact? {
        val lower = file.name.lowercase(Locale.ROOT)
        val (kind, contentType) = when {
            lower == "report.html" -> ArtifactKind.REPORT_HTML to ContentType.Text.Html.toString()
            lower == "diff.json" -> ArtifactKind.DIFF_JSON to ContentType.Application.Json.toString()
            lower.endsWith(".png") -> ArtifactKind.IMAGE to ContentType.Image.PNG.toString()
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> ArtifactKind.IMAGE to ContentType.Image.JPEG.toString()
            lower.endsWith(".webp") -> ArtifactKind.IMAGE to "image/webp"
            lower.endsWith(".gif") -> ArtifactKind.IMAGE to ContentType.Image.GIF.toString()
            lower.endsWith(".css") -> ArtifactKind.STATIC_ASSET to "text/css"
            lower.endsWith(".js") -> ArtifactKind.STATIC_ASSET to "application/javascript"
            else -> return null
        }

        return ScannedArtifact(
            kind = kind,
            filename = file.name,
            contentType = contentType,
            byteSize = file.fileSize(),
            storagePath = file.toAbsolutePath().toString(),
        )
    }

    private fun sanitizeFilename(filename: String): String {
        return filename
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(255)
            .ifBlank { "upload.bin" }
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
