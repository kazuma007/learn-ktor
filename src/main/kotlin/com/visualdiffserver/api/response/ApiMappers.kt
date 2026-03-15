package com.visualdiffserver.api.response

import com.visualdiffserver.domain.Artifact
import com.visualdiffserver.domain.Asset
import com.visualdiffserver.domain.Comparison
import com.visualdiffserver.domain.Project
import com.visualdiffserver.domain.Run

fun Project.toResponse(): ProjectResponse =
    ProjectResponse(id.toString(), name, createdAt.toString())

fun Asset.toResponse(): AssetResponse =
    AssetResponse(
        id = id.toString(),
        projectId = projectId.toString(),
        filename = filename,
        contentType = contentType,
        byteSize = byteSize,
        sha256 = sha256,
        storagePath = storagePath,
        createdAt = createdAt.toString(),
    )

fun Comparison.toResponse(): ComparisonResponse =
    ComparisonResponse(
        id = id.toString(),
        projectId = projectId.toString(),
        oldAssetId = oldAssetId.toString(),
        newAssetId = newAssetId.toString(),
        createdAt = createdAt.toString(),
    )

fun Run.toResponse(): RunResponse =
    RunResponse(
        id = id.toString(),
        comparisonId = comparisonId.toString(),
        status = status,
        startedAt = startedAt?.toString(),
        finishedAt = finishedAt?.toString(),
        exitCode = exitCode,
        stdout = stdout,
        stderr = stderr,
        errorText = errorText,
        outputDir = outputDir,
        createdAt = createdAt.toString(),
    )

fun Artifact.toResponse(): ArtifactResponse =
    ArtifactResponse(
        id = id.toString(),
        runId = runId.toString(),
        kind = kind,
        filename = filename,
        contentType = contentType,
        byteSize = byteSize,
        storagePath = storagePath,
        createdAt = createdAt.toString(),
    )
