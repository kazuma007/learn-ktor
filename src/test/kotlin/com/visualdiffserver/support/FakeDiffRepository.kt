package com.visualdiffserver.support

import com.visualdiffserver.domain.Artifact
import com.visualdiffserver.domain.ArtifactKind
import com.visualdiffserver.domain.Asset
import com.visualdiffserver.domain.Comparison
import com.visualdiffserver.domain.DiffRepository
import com.visualdiffserver.domain.NewAsset
import com.visualdiffserver.domain.Project
import com.visualdiffserver.domain.QueuedRunWork
import com.visualdiffserver.domain.Run
import com.visualdiffserver.domain.RunQueueRepository
import com.visualdiffserver.domain.RunStatus
import com.visualdiffserver.storage.StorageService
import java.time.Instant
import java.util.UUID

class FakeDiffRepository : DiffRepository, RunQueueRepository {
    private val projects = mutableMapOf<UUID, Project>()
    private val assets = mutableMapOf<UUID, Asset>()
    private val comparisons = mutableMapOf<UUID, Comparison>()
    private val runs = mutableMapOf<UUID, Run>()
    private val artifacts = mutableMapOf<UUID, MutableList<Artifact>>()
    private val queuedRuns = ArrayDeque<QueuedRunWork>()

    override suspend fun createProject(name: String): Project {
        val now = Instant.now()
        val id = UUID.randomUUID()
        return Project(id = id, name = name, createdAt = now).also { projects[id] = it }
    }

    override suspend fun projectExists(projectId: UUID): Boolean = projects.containsKey(projectId)

    override suspend fun createAsset(projectId: UUID, newAsset: NewAsset): Asset {
        val now = Instant.now()
        val id = UUID.randomUUID()
        return Asset(
                id = id,
                projectId = projectId,
                filename = newAsset.filename,
                contentType = newAsset.contentType,
                byteSize = newAsset.byteSize,
                sha256 = newAsset.sha256,
                storagePath = newAsset.storagePath,
                createdAt = now,
            )
            .also { assets[id] = it }
    }

    override suspend fun getAsset(assetId: UUID): Asset? = assets[assetId]

    override suspend fun createComparison(
        projectId: UUID,
        oldAssetId: UUID,
        newAssetId: UUID,
    ): Comparison? {
        val oldAsset = assets[oldAssetId] ?: return null
        val newAsset = assets[newAssetId] ?: return null
        if (oldAsset.projectId != projectId || newAsset.projectId != projectId) return null

        val now = Instant.now()
        val id = UUID.randomUUID()
        return Comparison(
                id = id,
                projectId = projectId,
                oldAssetId = oldAssetId,
                newAssetId = newAssetId,
                createdAt = now,
            )
            .also { comparisons[id] = it }
    }

    override suspend fun getComparison(comparisonId: UUID): Comparison? = comparisons[comparisonId]

    override suspend fun createRun(runId: UUID, comparisonId: UUID, outputDir: String): Run {
        val now = Instant.now()
        return Run(
                id = runId,
                comparisonId = comparisonId,
                status = RunStatus.QUEUED.name,
                startedAt = null,
                finishedAt = null,
                exitCode = null,
                stdout = null,
                stderr = null,
                errorText = null,
                outputDir = outputDir,
                createdAt = now,
            )
            .also { runs[runId] = it }
    }

    override suspend fun getRun(runId: UUID): Run? = runs[runId]

    override suspend fun listArtifacts(runId: UUID): List<Artifact> =
        artifacts[runId]?.sortedBy { it.filename }.orEmpty()

    override suspend fun getArtifact(runId: UUID, artifactId: UUID): Artifact? {
        return artifacts[runId]?.firstOrNull { it.id == artifactId }
    }

    override suspend fun getReportArtifact(runId: UUID): Artifact? {
        return artifacts[runId]?.firstOrNull { it.kind == ArtifactKind.REPORT_HTML.name }
    }

    override suspend fun claimNextQueuedRun(): QueuedRunWork? {
        val job = queuedRuns.removeFirstOrNull() ?: return null
        val runId = job.runId
        val current = runs[runId] ?: return null
        runs[runId] = current.copy(status = RunStatus.RUNNING.name, startedAt = Instant.now())
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
        val now = Instant.now()
        runs[runId] =
            current.copy(
                status = RunStatus.SUCCEEDED.name,
                finishedAt = now,
                exitCode = exitCode,
                stdout = stdoutText,
                stderr = stderrText,
                errorText = null,
            )

        this.artifacts[runId] =
            artifacts
                .map {
                    Artifact(
                        id = UUID.randomUUID(),
                        runId = runId,
                        kind = it.kind.name,
                        filename = it.filename,
                        contentType = it.contentType,
                        byteSize = it.byteSize,
                        storagePath = it.storagePath,
                        createdAt = now,
                    )
                }
                .toMutableList()
    }

    override suspend fun saveRunResultFailure(
        runId: UUID,
        exitCode: Int?,
        stdoutText: String?,
        stderrText: String?,
        errorText: String,
    ) {
        val current = runs[runId] ?: return
        runs[runId] =
            current.copy(
                status = RunStatus.FAILED.name,
                finishedAt = Instant.now(),
                exitCode = exitCode,
                stdout = stdoutText,
                stderr = stderrText,
                errorText = errorText,
            )
    }

    suspend fun enqueueRun(oldFilePath: String, newFilePath: String, outputDir: String): UUID {
        val project = createProject("test-project")
        val projectId = project.id

        val oldAsset =
            createAsset(projectId, NewAsset("old.pdf", "application/pdf", 1, "old", oldFilePath))
        val newAsset =
            createAsset(projectId, NewAsset("new.pdf", "application/pdf", 1, "new", newFilePath))

        val comparison =
            createComparison(
                projectId = projectId,
                oldAssetId = oldAsset.id,
                newAssetId = newAsset.id,
            ) ?: error("comparison was not created")

        val runId = UUID.randomUUID()
        createRun(runId, comparison.id, outputDir)
        queuedRuns.addLast(
            QueuedRunWork(
                runId = runId,
                oldFilePath = oldFilePath,
                newFilePath = newFilePath,
                outputDir = outputDir,
            )
        )
        return runId
    }
}
