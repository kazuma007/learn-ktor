package com.visualdiffserver.domain

data class Project(val id: String, val name: String, val createdAt: String)

data class Asset(
    val id: String,
    val projectId: String,
    val filename: String,
    val contentType: String,
    val byteSize: Long,
    val sha256: String,
    val storagePath: String,
    val createdAt: String,
)

data class Comparison(
    val id: String,
    val projectId: String,
    val oldAssetId: String,
    val newAssetId: String,
    val createdAt: String,
)

data class Run(
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

data class Artifact(
    val id: String,
    val runId: String,
    val kind: String,
    val filename: String,
    val contentType: String,
    val byteSize: Long,
    val storagePath: String,
    val createdAt: String,
)
