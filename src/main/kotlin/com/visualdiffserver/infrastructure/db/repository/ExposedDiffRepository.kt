package com.visualdiffserver.infrastructure.db.repository

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
import com.visualdiffserver.infrastructure.db.DatabaseFactory
import com.visualdiffserver.infrastructure.db.mapper.toArtifact
import com.visualdiffserver.infrastructure.db.mapper.toAsset
import com.visualdiffserver.infrastructure.db.mapper.toComparison
import com.visualdiffserver.infrastructure.db.mapper.toRun
import com.visualdiffserver.infrastructure.db.tables.ArtifactsTable
import com.visualdiffserver.infrastructure.db.tables.AssetsTable
import com.visualdiffserver.infrastructure.db.tables.ComparisonsTable
import com.visualdiffserver.infrastructure.db.tables.ProjectsTable
import com.visualdiffserver.infrastructure.db.tables.RunsTable
import com.visualdiffserver.storage.StorageService
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class ExposedDiffRepository : DiffRepository, RunQueueRepository {
    override suspend fun createProject(name: String): Project =
        DatabaseFactory.query {
            val id = UUID.randomUUID()
            val now = Instant.now()
            ProjectsTable.insert {
                it[ProjectsTable.id] = id
                it[ProjectsTable.name] = name
                it[createdAt] = now
            }
            newProject(id = id, name = name, createdAt = now)
        }

    override suspend fun projectExists(projectId: UUID): Boolean =
        DatabaseFactory.query {
            ProjectsTable.selectAll().where { ProjectsTable.id eq projectId }.limit(1).any()
        }

    override suspend fun createAsset(projectId: UUID, newAsset: NewAsset): Asset =
        DatabaseFactory.query {
            val id = UUID.randomUUID()
            val now = Instant.now()
            AssetsTable.insert {
                it[AssetsTable.id] = id
                it[AssetsTable.projectId] = projectId
                it[filename] = newAsset.filename
                it[contentType] = newAsset.contentType
                it[byteSize] = newAsset.byteSize
                it[sha256] = newAsset.sha256
                it[storagePath] = newAsset.storagePath
                it[createdAt] = now
            }
            newAsset(id = id, projectId = projectId, newAsset = newAsset, createdAt = now)
        }

    override suspend fun getAsset(assetId: UUID): Asset? =
        DatabaseFactory.query {
            AssetsTable.selectAll()
                .where { AssetsTable.id eq assetId }
                .limit(1)
                .map { it.toAsset() }
                .singleOrNull()
        }

    override suspend fun createComparison(
        projectId: UUID,
        oldAssetId: UUID,
        newAssetId: UUID,
    ): Comparison? =
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
            newComparison(id, projectId, oldAssetId, newAssetId, now)
        }

    override suspend fun getComparison(comparisonId: UUID): Comparison? =
        DatabaseFactory.query {
            ComparisonsTable.selectAll()
                .where { ComparisonsTable.id eq comparisonId }
                .limit(1)
                .map { it.toComparison() }
                .singleOrNull()
        }

    override suspend fun createRun(runId: UUID, comparisonId: UUID, outputDir: String): Run =
        DatabaseFactory.query {
            val now = Instant.now()
            RunsTable.insert {
                it[RunsTable.id] = runId
                it[RunsTable.comparisonId] = comparisonId
                it[status] = RunStatus.QUEUED.name
                it[RunsTable.outputDir] = outputDir
                it[createdAt] = now
            }

            newQueuedRun(runId, comparisonId, outputDir, now)
        }

    override suspend fun getRun(runId: UUID): Run? =
        DatabaseFactory.query {
            RunsTable.selectAll()
                .where { RunsTable.id eq runId }
                .limit(1)
                .map { it.toRun() }
                .singleOrNull()
        }

    override suspend fun listArtifacts(runId: UUID): List<Artifact> =
        DatabaseFactory.query {
            ArtifactsTable.selectAll()
                .where { ArtifactsTable.runId eq runId }
                .orderBy(ArtifactsTable.filename to SortOrder.ASC)
                .map { it.toArtifact() }
        }

    override suspend fun getArtifact(runId: UUID, artifactId: UUID): Artifact? =
        DatabaseFactory.query {
            ArtifactsTable.selectAll()
                .where { (ArtifactsTable.id eq artifactId) and (ArtifactsTable.runId eq runId) }
                .limit(1)
                .map { it.toArtifact() }
                .singleOrNull()
        }

    override suspend fun getReportArtifact(runId: UUID): Artifact? =
        DatabaseFactory.query {
            ArtifactsTable.selectAll()
                .where {
                    (ArtifactsTable.runId eq runId) and
                        (ArtifactsTable.kind eq ArtifactKind.REPORT_HTML.name)
                }
                .limit(1)
                .map { it.toArtifact() }
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

    private fun newProject(id: UUID, name: String, createdAt: Instant): Project =
        Project(id = id, name = name, createdAt = createdAt)

    private fun newAsset(id: UUID, projectId: UUID, newAsset: NewAsset, createdAt: Instant): Asset =
        Asset(
            id = id,
            projectId = projectId,
            filename = newAsset.filename,
            contentType = newAsset.contentType,
            byteSize = newAsset.byteSize,
            sha256 = newAsset.sha256,
            storagePath = newAsset.storagePath,
            createdAt = createdAt,
        )

    private fun newComparison(
        id: UUID,
        projectId: UUID,
        oldAssetId: UUID,
        newAssetId: UUID,
        createdAt: Instant,
    ): Comparison =
        Comparison(
            id = id,
            projectId = projectId,
            oldAssetId = oldAssetId,
            newAssetId = newAssetId,
            createdAt = createdAt,
        )

    private fun newQueuedRun(
        runId: UUID,
        comparisonId: UUID,
        outputDir: String,
        createdAt: Instant,
    ): Run =
        Run(
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
            createdAt = createdAt,
        )
}
