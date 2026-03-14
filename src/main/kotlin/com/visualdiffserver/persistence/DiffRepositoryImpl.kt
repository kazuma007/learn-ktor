package com.visualdiffserver.persistence

import com.visualdiffserver.domain.ArtifactKind
import com.visualdiffserver.domain.ArtifactResponse
import com.visualdiffserver.domain.AssetResponse
import com.visualdiffserver.domain.ComparisonResponse
import com.visualdiffserver.domain.ProjectResponse
import com.visualdiffserver.domain.RunResponse
import com.visualdiffserver.domain.RunStatus
import com.visualdiffserver.storage.StorageService
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class ExposedDiffRepository : DiffRepository {
    override suspend fun createProject(name: String): ProjectResponse =
        DatabaseFactory.query {
            val id = UUID.randomUUID()
            val now = Instant.now()
            ProjectsTable.insert {
                it[ProjectsTable.id] = id
                it[ProjectsTable.name] = name
                it[createdAt] = now
            }
            ProjectResponse(id.toString(), name, now.toString())
        }

    override suspend fun projectExists(projectId: UUID): Boolean =
        DatabaseFactory.query {
            ProjectsTable.selectAll().where { ProjectsTable.id eq projectId }.limit(1).any()
        }

    override suspend fun createAsset(
        projectId: UUID,
        stored: StorageService.StoredFile,
    ): AssetResponse =
        DatabaseFactory.query {
            val id = UUID.randomUUID()
            val now = Instant.now()
            AssetsTable.insert {
                it[AssetsTable.id] = id
                it[AssetsTable.projectId] = projectId
                it[filename] = stored.filename
                it[contentType] = stored.contentType
                it[byteSize] = stored.byteSize
                it[sha256] = stored.sha256
                it[storagePath] = stored.storagePath
                it[createdAt] = now
            }
            AssetResponse(
                id = id.toString(),
                projectId = projectId.toString(),
                filename = stored.filename,
                contentType = stored.contentType,
                byteSize = stored.byteSize,
                sha256 = stored.sha256,
                storagePath = stored.storagePath,
                createdAt = now.toString(),
            )
        }

    override suspend fun getAsset(assetId: UUID): AssetResponse? =
        DatabaseFactory.query {
            AssetsTable.selectAll()
                .where { AssetsTable.id eq assetId }
                .limit(1)
                .map { it.toAssetResponse() }
                .singleOrNull()
        }

    override suspend fun createComparison(
        projectId: UUID,
        oldAssetId: UUID,
        newAssetId: UUID,
    ): ComparisonResponse? =
        DatabaseFactory.query {
            val oldExists =
                AssetsTable.selectAll()
                    .where {
                        (AssetsTable.id eq oldAssetId) and (AssetsTable.projectId eq projectId)
                    }
                    .limit(1)
                    .any()
            val newExists =
                AssetsTable.selectAll()
                    .where {
                        (AssetsTable.id eq newAssetId) and (AssetsTable.projectId eq projectId)
                    }
                    .limit(1)
                    .any()

            if (!oldExists || !newExists) return@query null

            val id = UUID.randomUUID()
            val now = Instant.now()
            ComparisonsTable.insert {
                it[ComparisonsTable.id] = id
                it[ComparisonsTable.projectId] = projectId
                it[ComparisonsTable.oldAssetId] = oldAssetId
                it[ComparisonsTable.newAssetId] = newAssetId
                it[createdAt] = now
            }
            ComparisonResponse(
                id = id.toString(),
                projectId = projectId.toString(),
                oldAssetId = oldAssetId.toString(),
                newAssetId = newAssetId.toString(),
                createdAt = now.toString(),
            )
        }

    override suspend fun getComparison(comparisonId: UUID): ComparisonResponse? =
        DatabaseFactory.query {
            ComparisonsTable.selectAll()
                .where { ComparisonsTable.id eq comparisonId }
                .limit(1)
                .map { it.toComparisonResponse() }
                .singleOrNull()
        }

    override suspend fun createRun(
        runId: UUID,
        comparisonId: UUID,
        outputDir: String,
    ): RunResponse =
        DatabaseFactory.query {
            val now = Instant.now()
            RunsTable.insert {
                it[RunsTable.id] = runId
                it[RunsTable.comparisonId] = comparisonId
                it[status] = RunStatus.QUEUED.name
                it[RunsTable.outputDir] = outputDir
                it[createdAt] = now
            }

            RunResponse(
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
                createdAt = now.toString(),
            )
        }

    override suspend fun getRun(runId: UUID): RunResponse? =
        DatabaseFactory.query {
            RunsTable.selectAll()
                .where { RunsTable.id eq runId }
                .limit(1)
                .map { it.toRunResponse() }
                .singleOrNull()
        }

    override suspend fun listArtifacts(runId: UUID): List<ArtifactResponse> =
        DatabaseFactory.query {
            ArtifactsTable.selectAll()
                .where { ArtifactsTable.runId eq runId }
                .orderBy(ArtifactsTable.filename to SortOrder.ASC)
                .map { it.toArtifactResponse() }
        }

    override suspend fun getArtifact(runId: UUID, artifactId: UUID): ArtifactResponse? =
        DatabaseFactory.query {
            ArtifactsTable.selectAll()
                .where { (ArtifactsTable.id eq artifactId) and (ArtifactsTable.runId eq runId) }
                .limit(1)
                .map { it.toArtifactResponse() }
                .singleOrNull()
        }

    override suspend fun getReportArtifact(runId: UUID): ArtifactResponse? =
        DatabaseFactory.query {
            ArtifactsTable.selectAll()
                .where {
                    (ArtifactsTable.runId eq runId) and
                        (ArtifactsTable.kind eq ArtifactKind.REPORT_HTML.name)
                }
                .limit(1)
                .map { it.toArtifactResponse() }
                .singleOrNull()
        }

    override suspend fun claimNextQueuedRun(): QueuedRunWork? =
        DatabaseFactory.query {
            val nextRun =
                RunsTable.selectAll()
                    .where { RunsTable.status eq RunStatus.QUEUED.name }
                    .limit(1)
                    .firstOrNull() ?: return@query null

            val runId = nextRun[RunsTable.id]
            val comparisonId = nextRun[RunsTable.comparisonId]

            val comparison =
                ComparisonsTable.selectAll()
                    .where { ComparisonsTable.id eq comparisonId }
                    .limit(1)
                    .firstOrNull() ?: return@query null

            val oldAsset =
                AssetsTable.selectAll()
                    .where { AssetsTable.id eq comparison[ComparisonsTable.oldAssetId] }
                    .limit(1)
                    .firstOrNull() ?: return@query null

            val newAsset =
                AssetsTable.selectAll()
                    .where { AssetsTable.id eq comparison[ComparisonsTable.newAssetId] }
                    .limit(1)
                    .firstOrNull() ?: return@query null

            val updated =
                RunsTable.update({
                    (RunsTable.id eq runId) and (RunsTable.status eq RunStatus.QUEUED.name)
                }) {
                    it[status] = RunStatus.RUNNING.name
                    it[startedAt] = Instant.now()
                }
            if (updated == 0) return@query null

            QueuedRunWork(
                runId = runId,
                oldFilePath = oldAsset[AssetsTable.storagePath],
                newFilePath = newAsset[AssetsTable.storagePath],
                outputDir = nextRun[RunsTable.outputDir],
            )
        }

    override suspend fun saveRunResultSuccess(
        runId: UUID,
        exitCode: Int,
        stdoutText: String,
        stderrText: String,
        artifacts: List<StorageService.ScannedArtifact>,
    ) {
        DatabaseFactory.query {
            ArtifactsTable.deleteWhere { ArtifactsTable.runId eq runId }

            val now = Instant.now()
            artifacts.forEach { artifact ->
                ArtifactsTable.insert {
                    it[id] = UUID.randomUUID()
                    it[ArtifactsTable.runId] = runId
                    it[kind] = artifact.kind.name
                    it[filename] = artifact.filename
                    it[contentType] = artifact.contentType
                    it[byteSize] = artifact.byteSize
                    it[storagePath] = artifact.storagePath
                    it[createdAt] = now
                }
            }

            RunsTable.update({ RunsTable.id eq runId }) {
                it[status] = RunStatus.SUCCEEDED.name
                it[finishedAt] = now
                it[RunsTable.exitCode] = exitCode
                it[stdout] = stdoutText
                it[stderr] = stderrText
                it[errorText] = null
            }
        }
    }

    override suspend fun saveRunResultFailure(
        runId: UUID,
        exitCode: Int?,
        stdoutText: String?,
        stderrText: String?,
        errorText: String,
    ) {
        DatabaseFactory.query {
            RunsTable.update({ RunsTable.id eq runId }) {
                it[status] = RunStatus.FAILED.name
                it[finishedAt] = Instant.now()
                it[RunsTable.exitCode] = exitCode
                it[stdout] = stdoutText
                it[stderr] = stderrText
                it[RunsTable.errorText] = errorText
            }
        }
    }
}

private fun ResultRow.toAssetResponse(): AssetResponse {
    return AssetResponse(
        id = this[AssetsTable.id].toString(),
        projectId = this[AssetsTable.projectId].toString(),
        filename = this[AssetsTable.filename],
        contentType = this[AssetsTable.contentType],
        byteSize = this[AssetsTable.byteSize],
        sha256 = this[AssetsTable.sha256],
        storagePath = this[AssetsTable.storagePath],
        createdAt = this[AssetsTable.createdAt].toString(),
    )
}

private fun ResultRow.toComparisonResponse(): ComparisonResponse {
    return ComparisonResponse(
        id = this[ComparisonsTable.id].toString(),
        projectId = this[ComparisonsTable.projectId].toString(),
        oldAssetId = this[ComparisonsTable.oldAssetId].toString(),
        newAssetId = this[ComparisonsTable.newAssetId].toString(),
        createdAt = this[ComparisonsTable.createdAt].toString(),
    )
}

private fun ResultRow.toRunResponse(): RunResponse {
    return RunResponse(
        id = this[RunsTable.id].toString(),
        comparisonId = this[RunsTable.comparisonId].toString(),
        status = this[RunsTable.status],
        startedAt = this[RunsTable.startedAt]?.toString(),
        finishedAt = this[RunsTable.finishedAt]?.toString(),
        exitCode = this[RunsTable.exitCode],
        stdout = this[RunsTable.stdout],
        stderr = this[RunsTable.stderr],
        errorText = this[RunsTable.errorText],
        outputDir = this[RunsTable.outputDir],
        createdAt = this[RunsTable.createdAt].toString(),
    )
}

private fun ResultRow.toArtifactResponse(): ArtifactResponse {
    return ArtifactResponse(
        id = this[ArtifactsTable.id].toString(),
        runId = this[ArtifactsTable.runId].toString(),
        kind = this[ArtifactsTable.kind],
        filename = this[ArtifactsTable.filename],
        contentType = this[ArtifactsTable.contentType],
        byteSize = this[ArtifactsTable.byteSize],
        storagePath = this[ArtifactsTable.storagePath],
        createdAt = this[ArtifactsTable.createdAt].toString(),
    )
}
