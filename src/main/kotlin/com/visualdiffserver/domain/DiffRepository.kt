package com.visualdiffserver.domain

import com.visualdiffserver.storage.StorageService
import java.util.UUID

interface DiffRepository {
    suspend fun createProject(name: String): Project

    suspend fun projectExists(projectId: UUID): Boolean

    suspend fun createAsset(projectId: UUID, stored: StorageService.StoredFile): Asset

    suspend fun getAsset(assetId: UUID): Asset?

    suspend fun createComparison(projectId: UUID, oldAssetId: UUID, newAssetId: UUID): Comparison?

    suspend fun getComparison(comparisonId: UUID): Comparison?

    suspend fun createRun(runId: UUID, comparisonId: UUID, outputDir: String): Run

    suspend fun getRun(runId: UUID): Run?

    suspend fun listArtifacts(runId: UUID): List<Artifact>

    suspend fun getArtifact(runId: UUID, artifactId: UUID): Artifact?

    suspend fun getReportArtifact(runId: UUID): Artifact?

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
