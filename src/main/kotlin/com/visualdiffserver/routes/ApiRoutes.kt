package com.visualdiffserver.routes

import com.visualdiffserver.api.request.CreateComparisonRequest
import com.visualdiffserver.api.request.CreateProjectRequest
import com.visualdiffserver.api.response.toResponse
import com.visualdiffserver.application.DiffService
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureApiRoutes() {
    val diffService by inject<DiffService>()

    routing {
        route("/api") {
            post("/projects") {
                val request = call.receive<CreateProjectRequest>()
                val response = diffService.createProject(request.name)
                call.respond(HttpStatusCode.Created, response.toResponse())
            }

            post("/projects/{projectId}/assets") {
                val projectId = call.requireUuidPathParam("projectId")
                val upload = call.receiveAssetUpload()
                val response = diffService.createAsset(projectId, upload)
                call.respond(HttpStatusCode.Created, response.toResponse())
            }

            get("/assets/{assetId}") {
                val assetId = call.requireUuidPathParam("assetId")
                call.respond(diffService.getAsset(assetId).toResponse())
            }

            get("/assets/{assetId}/download") {
                val assetId = call.requireUuidPathParam("assetId")
                val file = diffService.getAssetDownload(assetId)

                call.response.headers.append(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                            ContentDisposition.Parameters.FileName,
                            file.filename,
                        )
                        .toString(),
                )
                call.respondFile(file.path.toFile())
            }

            post("/projects/{projectId}/comparisons") {
                val projectId = call.requireUuidPathParam("projectId")
                val body = call.receive<CreateComparisonRequest>()
                val oldAssetId =
                    body.oldAssetId.toUuidOrNull()
                        ?: throw ApiException(HttpStatusCode.BadRequest, "oldAssetId must be UUID")
                val newAssetId =
                    body.newAssetId.toUuidOrNull()
                        ?: throw ApiException(HttpStatusCode.BadRequest, "newAssetId must be UUID")

                val comparison = diffService.createComparison(projectId, oldAssetId, newAssetId)
                call.respond(HttpStatusCode.Created, comparison.toResponse())
            }

            post("/comparisons/{comparisonId}/runs") {
                val comparisonId = call.requireUuidPathParam("comparisonId")
                val run = diffService.createRun(comparisonId)
                call.respond(HttpStatusCode.Created, run.toResponse())
            }

            get("/runs/{runId}") {
                val runId = call.requireUuidPathParam("runId")
                call.respond(diffService.getRun(runId).toResponse())
            }

            get("/runs/{runId}/artifacts") {
                val runId = call.requireUuidPathParam("runId")
                call.respond(diffService.listArtifacts(runId).map { it.toResponse() })
            }

            get("/runs/{runId}/artifacts/{artifactId}") {
                val runId = call.requireUuidPathParam("runId")
                val artifactId = call.requireUuidPathParam("artifactId")
                val path = diffService.getArtifactDownload(runId, artifactId)
                call.respondFile(path.toFile())
            }

            get("/runs/{runId}/report") {
                val runId = call.requireUuidPathParam("runId")
                val path = diffService.getReportDownload(runId)
                call.respondFile(path.toFile())
            }
        }
    }
}
