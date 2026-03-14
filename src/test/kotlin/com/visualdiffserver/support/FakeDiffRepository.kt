package com.visualdiffserver.support

import com.visualdiffserver.domain.ArtifactKind
import com.visualdiffserver.domain.ArtifactResponse
import com.visualdiffserver.domain.AssetResponse
import com.visualdiffserver.domain.ComparisonResponse
import com.visualdiffserver.domain.ProjectResponse
import com.visualdiffserver.domain.RunResponse
import com.visualdiffserver.domain.RunStatus
import com.visualdiffserver.persistence.DiffRepository
import com.visualdiffserver.persistence.QueuedRunWork
import com.visualdiffserver.storage.StorageService
import java.time.Instant
import java.util.UUID

class FakeDiffRepository : DiffRepository {
    private val projects = mutableMapOf<UUID, ProjectResponse>()
    private val assets = mutableMapOf<UUID, AssetResponse>()
    private val comparisons = mutableMapOf<UUID, ComparisonResponse>()
    private val runs = mutableMapOf<UUID, RunResponse>()
    private val artifacts = mutableMapOf<UUID, MutableList<ArtifactResponse>>()
    private val queuedRuns = ArrayDeque<QueuedRunWork>()

    override suspend fun createProject(name: String): ProjectResponse {
        val now = Instant.now().toString()
        val id = UUID.randomUUID()
        return ProjectResponse(id = id.toString(), name = name, createdAt = now)
            .also { projects[id] = it }
    }

    override suspend fun projectExists(projectId: UUID): Boolean = projects.containsKey(projectId)

    override suspend fun createAsset(projectId: UUID, stored: StorageService.StoredFile): AssetResponse {
        val now = Instant.now().toString()
        val id = UUID.randomUUID()
        return AssetResponse(
            id = id.toString(),
            projectId = projectId.toString(),
            filename = stored.filename,
            contentType = stored.contentType,
            byteSize = stored.byteSize,
            sha256 = stored.sha256,
            storagePath = stored.storagePath,
            createdAt = now,
        ).also { assets[id] = it }
    }

    override suspend fun getAsset(assetId: UUID): AssetResponse? = assets[assetId]

    override suspend fun createComparison(projectId: UUID, oldAssetId: UUID, newAssetId: UUID): ComparisonResponse? {
        val oldAsset = assets[oldAssetId] ?: return null
        val newAsset = assets[newAssetId] ?: return null
        if (oldAsset.projectId != projectId.toString() || newAsset.projectId != projectId.toString()) return null

        val now = Instant.now().toString()
        val id = UUID.randomUUID()
        return ComparisonResponse(
            id = id.toString(),
            projectId = projectId.toString(),
            oldAssetId = oldAssetId.toString(),
            newAssetId = newAssetId.toString(),
            createdAt = now,
        ).also { comparisons[id] = it }
    }

    override suspend fun getComparison(comparisonId: UUID): ComparisonResponse? = comparisons[comparisonId]

    override suspend fun createRun(runId: UUID, comparisonId: UUID, outputDir: String): RunResponse {
        val now = Instant.now().toString()
        return RunResponse(
            id = runId.toString(),
            comparisonId = comparisonId.toString(),
            status = RunStatus.QUEUED.name,
            startedAt = null,
            finishedAt = null,
            exitCode = null,
            stdout = null,
            stderr = null,
            errorText = null,
            outputDir = outputDir,
            createdAt = now,
        ).also {
            runs[runId] = it
        }
    }

    override suspend fun getRun(runId: UUID): RunResponse? = runs[runId]

    override suspend fun listArtifacts(runId: UUID): List<ArtifactResponse> =
        artifacts[runId]
            ?.sortedBy { it.filename }
            .orEmpty()

    override suspend fun getArtifact(runId: UUID, artifactId: UUID): ArtifactResponse? {
        return artifacts[runId]?.firstOrNull { it.id == artifactId.toString() }
    }

    override suspend fun getReportArtifact(runId: UUID): ArtifactResponse? {
        return artifacts[runId]?.firstOrNull { it.kind == ArtifactKind.REPORT_HTML.name }
    }

    override suspend fun claimNextQueuedRun(): QueuedRunWork? {
        val job = queuedRuns.removeFirstOrNull() ?: return null
        val runId = job.runId
        val current = runs[runId] ?: return null
        runs[runId] = current.copy(status = RunStatus.RUNNING.name, startedAt = Instant.now().toString())
        return job
    }

    override suspend fun saveRunResultSuccess(
        runId: UUID,
        exitCode: Int,
        stdoutText: String,
        stderrText: String,
        artifacts: List<StorageService.ScannedArtifact>,
    ) {
        val current = runs[runId] ?: return
        val now = Instant.now().toString()
        runs[runId] = current.copy(
            status = RunStatus.SUCCEEDED.name,
            finishedAt = now,
            exitCode = exitCode,
            stdout = stdoutText,
            stderr = stderrText,
            errorText = null,
        )

        this.artifacts[runId] = artifacts.map {
            ArtifactResponse(
                id = UUID.randomUUID().toString(),
                runId = runId.toString(),
                kind = it.kind.name,
                filename = it.filename,
                contentType = it.contentType,
                byteSize = it.byteSize,
                storagePath = it.storagePath,
                createdAt = now,
            )
        }.toMutableList()
    }

    override suspend fun saveRunResultFailure(
        runId: UUID,
        exitCode: Int?,
        stdoutText: String?,
        stderrText: String?,
        errorText: String,
    ) {
        val current = runs[runId] ?: return
        runs[runId] = current.copy(
            status = RunStatus.FAILED.name,
            finishedAt = Instant.now().toString(),
            exitCode = exitCode,
            stdout = stdoutText,
            stderr = stderrText,
            errorText = errorText,
        )
    }

    suspend fun enqueueRun(
        oldFilePath: String,
        newFilePath: String,
        outputDir: String,
    ): UUID {
        val project = createProject("test-project")
        val projectId = UUID.fromString(project.id)

        val oldAsset = createAsset(
            projectId,
            StorageService.StoredFile("old.pdf", "application/pdf", 1, "old", oldFilePath),
        )
        val newAsset = createAsset(
            projectId,
            StorageService.StoredFile("new.pdf", "application/pdf", 1, "new", newFilePath),
        )

        val comparison = createComparison(
            projectId = projectId,
            oldAssetId = UUID.fromString(oldAsset.id),
            newAssetId = UUID.fromString(newAsset.id),
        ) ?: error("comparison was not created")

        val runId = UUID.randomUUID()
        createRun(runId, UUID.fromString(comparison.id), outputDir)
        queuedRuns.addLast(
            QueuedRunWork(
                runId = runId,
                oldFilePath = oldFilePath,
                newFilePath = newFilePath,
                outputDir = outputDir,
            ),
        )
        return runId
    }
}
