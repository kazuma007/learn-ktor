package com.visualdiffserver.persistence

import com.visualdiffserver.domain.ArtifactResponse
import com.visualdiffserver.domain.AssetResponse
import com.visualdiffserver.domain.ComparisonResponse
import com.visualdiffserver.domain.ProjectResponse
import com.visualdiffserver.domain.RunResponse
import com.visualdiffserver.storage.StorageService
import java.util.UUID

interface DiffRepository {
    suspend fun createProject(name: String): ProjectResponse
    suspend fun projectExists(projectId: UUID): Boolean

    suspend fun createAsset(projectId: UUID, stored: StorageService.StoredFile): AssetResponse
    suspend fun getAsset(assetId: UUID): AssetResponse?

    suspend fun createComparison(projectId: UUID, oldAssetId: UUID, newAssetId: UUID): ComparisonResponse?
    suspend fun getComparison(comparisonId: UUID): ComparisonResponse?

    suspend fun createRun(runId: UUID, comparisonId: UUID, outputDir: String): RunResponse
    suspend fun getRun(runId: UUID): RunResponse?

    suspend fun listArtifacts(runId: UUID): List<ArtifactResponse>
    suspend fun getArtifact(runId: UUID, artifactId: UUID): ArtifactResponse?
    suspend fun getReportArtifact(runId: UUID): ArtifactResponse?

    suspend fun claimNextQueuedRun(): QueuedRunWork?

    suspend fun saveRunResultSuccess(
        runId: UUID,
        exitCode: Int,
        stdoutText: String,
        stderrText: String,
        artifacts: List<StorageService.ScannedArtifact>,
    )

    suspend fun saveRunResultFailure(
        runId: UUID,
        exitCode: Int?,
        stdoutText: String?,
        stderrText: String?,
        errorText: String,
    )
}

data class QueuedRunWork(
    val runId: UUID,
    val oldFilePath: String,
    val newFilePath: String,
    val outputDir: String,
)
