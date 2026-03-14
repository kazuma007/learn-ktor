package com.visualdiffserver.domain

import kotlinx.serialization.Serializable

@Serializable
data class ProjectResponse(
    val id: String,
    val name: String,
    val createdAt: String,
)

@Serializable
data class AssetResponse(
    val id: String,
    val projectId: String,
    val filename: String,
    val contentType: String,
    val byteSize: Long,
    val sha256: String,
    val storagePath: String,
    val createdAt: String,
)

@Serializable
data class ComparisonResponse(
    val id: String,
    val projectId: String,
    val oldAssetId: String,
    val newAssetId: String,
    val createdAt: String,
)

@Serializable
data class RunResponse(
    val id: String,
    val comparisonId: String,
    val status: String,
    val startedAt: String?,
    val finishedAt: String?,
    val exitCode: Int?,
    val stdout: String?,
    val stderr: String?,
    val errorText: String?,
    val outputDir: String,
    val createdAt: String,
)

@Serializable
data class ArtifactResponse(
    val id: String,
    val runId: String,
    val kind: String,
    val filename: String,
    val contentType: String,
    val byteSize: Long,
    val storagePath: String,
    val createdAt: String,
)
