package com.visualdiffserver.domain

import java.time.Instant
import java.util.UUID

data class Project(val id: UUID, val name: String, val createdAt: Instant)

data class NewAsset(
    val filename: String,
    val contentType: String,
    val byteSize: Long,
    val sha256: String,
    val storagePath: String,
)

data class Asset(
    val id: UUID,
    val projectId: UUID,
    val filename: String,
    val contentType: String,
    val byteSize: Long,
    val sha256: String,
    val storagePath: String,
    val createdAt: Instant,
)

data class Comparison(
    val id: UUID,
    val projectId: UUID,
    val oldAssetId: UUID,
    val newAssetId: UUID,
    val createdAt: Instant,
)

data class Run(
    val id: UUID,
    val comparisonId: UUID,
    val status: String,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val exitCode: Int?,
    val stdout: String?,
    val stderr: String?,
    val errorText: String?,
    val outputDir: String,
    val createdAt: Instant,
)

data class Artifact(
    val id: UUID,
    val runId: UUID,
    val kind: String,
    val filename: String,
    val contentType: String,
    val byteSize: Long,
    val storagePath: String,
    val createdAt: Instant,
)
