package com.visualdiffserver.infrastructure.db.mapper

import com.visualdiffserver.domain.Artifact
import com.visualdiffserver.domain.Asset
import com.visualdiffserver.domain.Comparison
import com.visualdiffserver.domain.Run
import com.visualdiffserver.infrastructure.db.tables.ArtifactsTable
import com.visualdiffserver.infrastructure.db.tables.AssetsTable
import com.visualdiffserver.infrastructure.db.tables.ComparisonsTable
import com.visualdiffserver.infrastructure.db.tables.RunsTable
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toAsset(): Asset =
    Asset(
        id = this[AssetsTable.id],
        projectId = this[AssetsTable.projectId],
        filename = this[AssetsTable.filename],
        contentType = this[AssetsTable.contentType],
        byteSize = this[AssetsTable.byteSize],
        sha256 = this[AssetsTable.sha256],
        storagePath = this[AssetsTable.storagePath],
        createdAt = this[AssetsTable.createdAt],
    )

fun ResultRow.toComparison(): Comparison =
    Comparison(
        id = this[ComparisonsTable.id],
        projectId = this[ComparisonsTable.projectId],
        oldAssetId = this[ComparisonsTable.oldAssetId],
        newAssetId = this[ComparisonsTable.newAssetId],
        createdAt = this[ComparisonsTable.createdAt],
    )

fun ResultRow.toRun(): Run =
    Run(
        id = this[RunsTable.id],
        comparisonId = this[RunsTable.comparisonId],
        status = this[RunsTable.status],
        startedAt = this[RunsTable.startedAt],
        finishedAt = this[RunsTable.finishedAt],
        exitCode = this[RunsTable.exitCode],
        stdout = this[RunsTable.stdout],
        stderr = this[RunsTable.stderr],
        errorText = this[RunsTable.errorText],
        outputDir = this[RunsTable.outputDir],
        createdAt = this[RunsTable.createdAt],
    )

fun ResultRow.toArtifact(): Artifact =
    Artifact(
        id = this[ArtifactsTable.id],
        runId = this[ArtifactsTable.runId],
        kind = this[ArtifactsTable.kind],
        filename = this[ArtifactsTable.filename],
        contentType = this[ArtifactsTable.contentType],
        byteSize = this[ArtifactsTable.byteSize],
        storagePath = this[ArtifactsTable.storagePath],
        createdAt = this[ArtifactsTable.createdAt],
    )
