package com.visualdiffserver.http

import com.visualdiffserver.domain.CreateComparisonRequest
import com.visualdiffserver.domain.CreateProjectRequest
import com.visualdiffserver.persistence.DiffRepository
import com.visualdiffserver.storage.StorageService
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.nio.file.Files
import java.util.UUID
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val repository by inject<DiffRepository>()
    val storage by inject<StorageService>()

    routing {
        route("/api") {
            post("/projects") {
                val request = call.receive<CreateProjectRequest>()
                if (request.name.isBlank()) {
                    throw ApiException(HttpStatusCode.BadRequest, "name must not be blank")
                }

                val response = repository.createProject(request.name.trim())
                call.respond(HttpStatusCode.Created, response)
            }

            post("/projects/{projectId}/assets") {
                val projectId = call.requireUuidPathParam("projectId")
                if (!repository.projectExists(projectId)) {
                    throw ApiException(HttpStatusCode.NotFound, "project not found")
                }

                val multipart = call.receiveMultipart()
                var uploadedAssetId: String? = null
                var responsePayload: Any? = null

                while (true) {
                    val part = multipart.readPart() ?: break
                    if (part is PartData.FileItem && uploadedAssetId == null) {
                        val originalFilename = part.originalFileName ?: "upload.bin"
                        val contentType = part.contentType?.toString() ?: "application/octet-stream"
                        val stored = storage.storeAsset(
                            originalFilename = originalFilename,
                            contentType = contentType,
                            data = part.provider().toInputStream(),
                        )
                        val response = repository.createAsset(projectId, stored)
                        uploadedAssetId = response.id
                        responsePayload = response
                    }
                    part.dispose()
                }

                val response = responsePayload ?: throw ApiException(HttpStatusCode.BadRequest, "file part is required")
                call.respond(HttpStatusCode.Created, response)
            }

            get("/assets/{assetId}") {
                val assetId = call.requireUuidPathParam("assetId")
                val asset = repository.getAsset(assetId)
                    ?: throw ApiException(HttpStatusCode.NotFound, "asset not found")
                call.respond(asset)
            }

            get("/assets/{assetId}/download") {
                val assetId = call.requireUuidPathParam("assetId")
                val asset = repository.getAsset(assetId)
                    ?: throw ApiException(HttpStatusCode.NotFound, "asset not found")

                val path = storage.readFile(asset.storagePath)
                if (!Files.exists(path)) {
                    throw ApiException(HttpStatusCode.NotFound, "stored file not found")
                }

                call.response.headers.append(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, asset.filename)
                        .toString(),
                )
                call.respondFile(path.toFile())
            }

            post("/projects/{projectId}/comparisons") {
                val projectId = call.requireUuidPathParam("projectId")
                if (!repository.projectExists(projectId)) {
                    throw ApiException(HttpStatusCode.NotFound, "project not found")
                }

                val body = call.receive<CreateComparisonRequest>()
                val oldAssetId = body.oldAssetId.toUuidOrNull()
                    ?: throw ApiException(HttpStatusCode.BadRequest, "oldAssetId must be UUID")
                val newAssetId = body.newAssetId.toUuidOrNull()
                    ?: throw ApiException(HttpStatusCode.BadRequest, "newAssetId must be UUID")

                val comparison = repository.createComparison(projectId, oldAssetId, newAssetId)
                    ?: throw ApiException(HttpStatusCode.BadRequest, "assets must belong to project")

                call.respond(HttpStatusCode.Created, comparison)
            }

            post("/comparisons/{comparisonId}/runs") {
                val comparisonId = call.requireUuidPathParam("comparisonId")
                repository.getComparison(comparisonId)
                    ?: throw ApiException(HttpStatusCode.NotFound, "comparison not found")

                val runId = UUID.randomUUID()
                val outputDir = storage.ensureRunOutputDir(runId).toAbsolutePath().toString()
                val run = repository.createRun(runId, comparisonId, outputDir)
                call.respond(HttpStatusCode.Created, run)
            }

            get("/runs/{runId}") {
                val runId = call.requireUuidPathParam("runId")
                val run = repository.getRun(runId)
                    ?: throw ApiException(HttpStatusCode.NotFound, "run not found")
                call.respond(run)
            }

            get("/runs/{runId}/artifacts") {
                val runId = call.requireUuidPathParam("runId")
                repository.getRun(runId)
                    ?: throw ApiException(HttpStatusCode.NotFound, "run not found")
                call.respond(repository.listArtifacts(runId))
            }

            get("/runs/{runId}/artifacts/{artifactId}") {
                val runId = call.requireUuidPathParam("runId")
                val artifactId = call.requireUuidPathParam("artifactId")

                val artifact = repository.getArtifact(runId, artifactId)
                    ?: throw ApiException(HttpStatusCode.NotFound, "artifact not found")

                val path = storage.readFile(artifact.storagePath)
                if (!Files.exists(path)) {
                    throw ApiException(HttpStatusCode.NotFound, "artifact file not found")
                }

                call.respondFile(path.toFile())
            }

            get("/runs/{runId}/report") {
                val runId = call.requireUuidPathParam("runId")
                val artifact = repository.getReportArtifact(runId)
                    ?: throw ApiException(HttpStatusCode.NotFound, "report not found")

                val path = storage.readFile(artifact.storagePath)
                if (!Files.exists(path)) {
                    throw ApiException(HttpStatusCode.NotFound, "report file not found")
                }

                call.respondFile(path.toFile())
            }
        }
    }
}

private fun ApplicationCall.requireUuidPathParam(name: String): UUID {
    val raw = parameters[name]
    return raw.toUuidOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "invalid $name")
}

private fun String?.toUuidOrNull(): UUID? {
    if (this == null) return null
    return try {
        UUID.fromString(this)
    } catch (_: IllegalArgumentException) {
        null
    }
}
