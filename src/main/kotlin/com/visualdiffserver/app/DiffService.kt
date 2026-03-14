package com.visualdiffserver.app

import com.visualdiffserver.domain.ArtifactResponse
import com.visualdiffserver.domain.AssetResponse
import com.visualdiffserver.domain.ComparisonResponse
import com.visualdiffserver.domain.ProjectResponse
import com.visualdiffserver.domain.RunResponse
import com.visualdiffserver.http.ApiException
import com.visualdiffserver.persistence.DiffRepository
import com.visualdiffserver.storage.StorageService
import io.ktor.http.HttpStatusCode
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class DiffService(
    private val repository: DiffRepository,
    private val storage: StorageService,
) {
    data class AssetUpload(
        val originalFilename: String,
        val contentType: String,
        val data: InputStream,
    )

    data class DownloadedFile(
        val filename: String,
        val path: Path,
    )

    suspend fun createProject(name: String): ProjectResponse {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "name must not be blank")
        }
        return repository.createProject(normalizedName)
    }

    suspend fun createAsset(projectId: UUID, upload: AssetUpload): AssetResponse {
        ensureProjectExists(projectId)

        val stored = storage.storeAsset(
            originalFilename = upload.originalFilename,
            contentType = upload.contentType,
            data = upload.data,
        )
        requireNonEmptyUpload(stored.byteSize)
        return repository.createAsset(projectId, stored)
    }

    suspend fun getAsset(assetId: UUID): AssetResponse {
        return repository.getAsset(assetId)
            ?: throw ApiException(HttpStatusCode.NotFound, "asset not found")
    }

    suspend fun getAssetDownload(assetId: UUID): DownloadedFile {
        val asset = getAsset(assetId)
        return DownloadedFile(
            filename = asset.filename,
            path = requireExistingFile(asset.storagePath, "stored file not found"),
        )
    }

    suspend fun createComparison(projectId: UUID, oldAssetId: UUID, newAssetId: UUID): ComparisonResponse {
        ensureProjectExists(projectId)

        return repository.createComparison(projectId, oldAssetId, newAssetId)
            ?: throw ApiException(HttpStatusCode.BadRequest, "assets must belong to project")
    }

    suspend fun createRun(comparisonId: UUID): RunResponse {
        repository.getComparison(comparisonId)
            ?: throw ApiException(HttpStatusCode.NotFound, "comparison not found")

        val runId = UUID.randomUUID()
        val outputDir = storage.ensureRunOutputDir(runId).toAbsolutePath().toString()
        return repository.createRun(runId, comparisonId, outputDir)
    }

    suspend fun getRun(runId: UUID): RunResponse {
        return repository.getRun(runId)
            ?: throw ApiException(HttpStatusCode.NotFound, "run not found")
    }

    suspend fun listArtifacts(runId: UUID): List<ArtifactResponse> {
        getRun(runId)
        return repository.listArtifacts(runId)
    }

    suspend fun getArtifactDownload(runId: UUID, artifactId: UUID): Path {
        val artifact = repository.getArtifact(runId, artifactId)
            ?: throw ApiException(HttpStatusCode.NotFound, "artifact not found")
        return requireExistingFile(artifact.storagePath, "artifact file not found")
    }

    suspend fun getReportDownload(runId: UUID): Path {
        val artifact = repository.getReportArtifact(runId)
            ?: throw ApiException(HttpStatusCode.NotFound, "report not found")
        return requireExistingFile(artifact.storagePath, "report file not found")
    }

    private suspend fun ensureProjectExists(projectId: UUID) {
        if (!repository.projectExists(projectId)) {
            throw ApiException(HttpStatusCode.NotFound, "project not found")
        }
    }

    private fun requireExistingFile(storagePath: String, message: String): Path {
        val path = storage.readFile(storagePath)
        if (!Files.exists(path)) {
            throw ApiException(HttpStatusCode.NotFound, message)
        }
        return path
    }

    private fun requireNonEmptyUpload(byteSize: Long) {
        if (byteSize <= 0) {
            throw ApiException(HttpStatusCode.BadRequest, "uploaded file must not be empty")
        }
    }
}
