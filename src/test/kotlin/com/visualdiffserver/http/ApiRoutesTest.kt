package com.visualdiffserver.http

import com.visualdiffserver.app.DiffService
import com.visualdiffserver.config.AppConfig
import com.visualdiffserver.module as appModule
import com.visualdiffserver.persistence.DiffRepository
import com.visualdiffserver.support.FakeDiffRepository
import com.visualdiffserver.storage.StorageService
import com.visualdiffserver.worker.CommandResult
import com.visualdiffserver.worker.VisualDiffRunner
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.core.module.Module
import org.koin.dsl.module

class ApiRoutesTest {
    @Test
    fun createProjectReturnsCreated() = testApplication {
        val testModule = testModule()
        application { appModule(initializeDatabase = false, rootModule = testModule) }

        val response = client.post("/api/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"demo"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"name\":\"demo\""))
    }

    @Test
    fun uploadAssetAndFetchMetadata() = testApplication {
        val testModule = testModule()
        application { appModule(initializeDatabase = false, rootModule = testModule) }

        val projectResponse = client.post("/api/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"asset-demo"}""")
        }
        val projectId = extractField(projectResponse.bodyAsText(), "id")

        val uploadResponse = client.post("/api/projects/$projectId/assets") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = "hello-pdf".encodeToByteArray(),
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
                                append(HttpHeaders.ContentDisposition, "filename=\"sample.pdf\"")
                            },
                        )
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.Created, uploadResponse.status)
        val assetId = extractField(uploadResponse.bodyAsText(), "id")

        val metadataResponse = client.get("/api/assets/$assetId")
        assertEquals(HttpStatusCode.OK, metadataResponse.status)
        assertTrue(metadataResponse.bodyAsText().contains("\"filename\":\"sample.pdf\""))

        val downloadResponse = client.get("/api/assets/$assetId/download")
        assertEquals(HttpStatusCode.OK, downloadResponse.status)
        assertEquals("hello-pdf", downloadResponse.bodyAsText())
    }

    @Test
    fun createProjectReturnsBadRequestForBlankName() = testApplication {
        val testModule = testModule()
        application { appModule(initializeDatabase = false, rootModule = testModule) }

        val response = client.post("/api/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"error":"name must not be blank"}""", response.bodyAsText())
    }

    @Test
    fun createAssetReturnsBadRequestWhenMultipartHasNoFilePart() = testApplication {
        val testModule = testModule()
        application { appModule(initializeDatabase = false, rootModule = testModule) }

        val projectResponse = client.post("/api/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"asset-demo"}""")
        }
        val projectId = extractField(projectResponse.bodyAsText(), "id")

        val response = client.post("/api/projects/$projectId/assets") {
            setBody(MultiPartFormDataContent(formData { }))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"error":"file part is required"}""", response.bodyAsText())
    }

    @Test
    fun getAssetReturnsBadRequestForInvalidUuid() = testApplication {
        val testModule = testModule()
        application { appModule(initializeDatabase = false, rootModule = testModule) }

        val response = client.get("/api/assets/not-a-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"error":"invalid assetId"}""", response.bodyAsText())
    }

    @Test
    fun createComparisonReturnsBadRequestForInvalidAssetUuid() = testApplication {
        val testModule = testModule()
        application { appModule(initializeDatabase = false, rootModule = testModule) }

        val projectResponse = client.post("/api/projects") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"comparison-demo"}""")
        }
        val projectId = extractField(projectResponse.bodyAsText(), "id")

        val response = client.post("/api/projects/$projectId/comparisons") {
            contentType(ContentType.Application.Json)
            setBody("""{"oldAssetId":"not-a-uuid","newAssetId":"123e4567-e89b-12d3-a456-426614174000"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"error":"oldAssetId must be UUID"}""", response.bodyAsText())
    }

    @Test
    fun getRunArtifactsReturnsNotFoundForMissingRun() = testApplication {
        val testModule = testModule()
        application { appModule(initializeDatabase = false, rootModule = testModule) }

        val response = client.get("/api/runs/123e4567-e89b-12d3-a456-426614174000/artifacts")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("""{"error":"run not found"}""", response.bodyAsText())
    }

    @Test
    fun createRunReturnsNotFoundForMissingComparison() = testApplication {
        val testModule = testModule()
        application { appModule(initializeDatabase = false, rootModule = testModule) }

        val response = client.post("/api/comparisons/123e4567-e89b-12d3-a456-426614174000/runs")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("""{"error":"comparison not found"}""", response.bodyAsText())
    }

    @Test
    fun getReportReturnsNotFoundWhenReportArtifactDoesNotExist() = testApplication {
        val testModule = testModule()
        application { appModule(initializeDatabase = false, rootModule = testModule) }

        val response = client.get("/api/runs/123e4567-e89b-12d3-a456-426614174000/report")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("""{"error":"report not found"}""", response.bodyAsText())
    }

    private fun testModule(): Module {
        val config = AppConfig(
            dbUrl = "jdbc:postgresql://unused",
            dbUser = "unused",
            dbPassword = "unused",
            dataDir = createTempDirectory("api-test-data"),
            assetsDir = createTempDirectory("api-test-assets"),
            runsDir = createTempDirectory("api-test-runs"),
            visualDiffCmd = "echo",
        )
        return module {
            single { config }
            single { StorageService(get()) }
            single<DiffRepository> { FakeDiffRepository() }
            single { DiffService(get(), get()) }
            single<VisualDiffRunner> {
                object : VisualDiffRunner {
                    override fun run(baseCommand: String, oldFile: String, newFile: String, outputDir: java.nio.file.Path): CommandResult {
                        return CommandResult(exitCode = 0, stdout = "", stderr = "")
                    }
                }
            }
        }
    }

    private fun extractField(json: String, field: String): String {
        val pattern = Regex("\"$field\":\"([^\"]+)\"")
        return pattern.find(json)?.groupValues?.get(1)
            ?: error("field '$field' not found in $json")
    }
}
