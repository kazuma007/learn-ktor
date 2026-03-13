package com.visualdiffserver.http

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
