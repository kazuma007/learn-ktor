package com.visualdiffserver.api.response

import com.visualdiffserver.domain.Artifact
import com.visualdiffserver.domain.Asset
import com.visualdiffserver.domain.Comparison
import com.visualdiffserver.domain.Project
import com.visualdiffserver.domain.Run

fun Project.toResponse(): ProjectResponse = ProjectResponse(id, name, createdAt)

fun Asset.toResponse(): AssetResponse =
    AssetResponse(id, projectId, filename, contentType, byteSize, sha256, storagePath, createdAt)

fun Comparison.toResponse(): ComparisonResponse =
    ComparisonResponse(id, projectId, oldAssetId, newAssetId, createdAt)

fun Run.toResponse(): RunResponse =
    RunResponse(
        id = id,
        comparisonId = comparisonId,
        status = status,
        startedAt = startedAt,
        finishedAt = finishedAt,
        exitCode = exitCode,
        stdout = stdout,
        stderr = stderr,
        errorText = errorText,
        outputDir = outputDir,
        createdAt = createdAt,
    )

fun Artifact.toResponse(): ArtifactResponse =
    ArtifactResponse(id, runId, kind, filename, contentType, byteSize, storagePath, createdAt)
