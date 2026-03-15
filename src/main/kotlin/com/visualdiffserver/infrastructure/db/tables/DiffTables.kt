package com.visualdiffserver.infrastructure.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ProjectsTable : Table("projects") {
    val id = uuid("id")
    val name = text("name")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object AssetsTable : Table("assets") {
    val id = uuid("id")
    val projectId =
        uuid("project_id").references(ProjectsTable.id, onDelete = ReferenceOption.CASCADE)
    val filename = text("filename")
    val contentType = text("content_type")
    val byteSize = long("byte_size")
    val sha256 = text("sha256")
    val storagePath = text("storage_path")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object ComparisonsTable : Table("comparisons") {
    val id = uuid("id")
    val projectId =
        uuid("project_id").references(ProjectsTable.id, onDelete = ReferenceOption.CASCADE)
    val oldAssetId =
        uuid("old_asset_id").references(AssetsTable.id, onDelete = ReferenceOption.RESTRICT)
    val newAssetId =
        uuid("new_asset_id").references(AssetsTable.id, onDelete = ReferenceOption.RESTRICT)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object RunsTable : Table("runs") {
    val id = uuid("id")
    val comparisonId =
        uuid("comparison_id").references(ComparisonsTable.id, onDelete = ReferenceOption.CASCADE)
    val status = text("status")
    val startedAt = timestamp("started_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()
    val exitCode = integer("exit_code").nullable()
    val stdout = text("stdout").nullable()
    val stderr = text("stderr").nullable()
    val errorText = text("error_text").nullable()
    val outputDir = text("output_dir")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object ArtifactsTable : Table("artifacts") {
    val id = uuid("id")
    val runId = uuid("run_id").references(RunsTable.id, onDelete = ReferenceOption.CASCADE)
    val kind = text("kind")
    val filename = text("filename")
    val contentType = text("content_type")
    val byteSize = long("byte_size")
    val storagePath = text("storage_path")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
