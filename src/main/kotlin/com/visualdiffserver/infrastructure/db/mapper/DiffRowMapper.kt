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
        id = this[AssetsTable.id].toString(),
        projectId = this[AssetsTable.projectId].toString(),
        filename = this[AssetsTable.filename],
        contentType = this[AssetsTable.contentType],
        byteSize = this[AssetsTable.byteSize],
        sha256 = this[AssetsTable.sha256],
        storagePath = this[AssetsTable.storagePath],
        createdAt = this[AssetsTable.createdAt].toString(),
    )

fun ResultRow.toComparison(): Comparison =
    Comparison(
        id = this[ComparisonsTable.id].toString(),
        projectId = this[ComparisonsTable.projectId].toString(),
        oldAssetId = this[ComparisonsTable.oldAssetId].toString(),
        newAssetId = this[ComparisonsTable.newAssetId].toString(),
        createdAt = this[ComparisonsTable.createdAt].toString(),
    )

fun ResultRow.toRun(): Run =
    Run(
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

fun ResultRow.toArtifact(): Artifact =
    Artifact(
        id = this[ArtifactsTable.id].toString(),
        runId = this[ArtifactsTable.runId].toString(),
        kind = this[ArtifactsTable.kind],
        filename = this[ArtifactsTable.filename],
        contentType = this[ArtifactsTable.contentType],
        byteSize = this[ArtifactsTable.byteSize],
        storagePath = this[ArtifactsTable.storagePath],
        createdAt = this[ArtifactsTable.createdAt].toString(),
    )
