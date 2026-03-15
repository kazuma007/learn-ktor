package com.visualdiffserver.routes

import com.visualdiffserver.application.DiffService
import com.visualdiffserver.config.AppConfig
import com.visualdiffserver.domain.DiffRepository
import com.visualdiffserver.domain.RunQueueRepository
import com.visualdiffserver.module as appModule
import com.visualdiffserver.storage.StorageService
import com.visualdiffserver.support.FakeDiffRepository
import com.visualdiffserver.worker.CommandResult
import com.visualdiffserver.worker.VisualDiffRunner
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import org.koin.core.module.Module
import org.koin.dsl.module

class AssetUploadRequestTest {
    @Test
    fun createAssetReturnsBadRequestForEmptyFile() = testApplication {
        val testModule = testModule()
        application { appModule(initializeDatabase = false, rootModule = testModule) }

        val projectResponse =
            client.post("/api/projects") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"asset-demo"}""")
            }
        val projectId = extractField(projectResponse.bodyAsText(), "id")

        val response =
            client.post("/api/projects/$projectId/assets") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                key = "file",
                                value = ByteArray(0),
                                headers =
                                    Headers.build {
                                        append(
                                            HttpHeaders.ContentType,
                                            ContentType.Application.Pdf.toString(),
                                        )
                                        append(
                                            HttpHeaders.ContentDisposition,
                                            "filename=\"empty.pdf\"",
                                        )
                                    },
                            )
                        }
                    )
                )
            }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("""{"error":"uploaded file must not be empty"}""", response.bodyAsText())
    }

    private fun testModule(): Module {
        val config =
            AppConfig(
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
            single { FakeDiffRepository() }
            single<DiffRepository> { get<FakeDiffRepository>() }
            single<RunQueueRepository> { get<FakeDiffRepository>() }
            single { DiffService(get(), get()) }
            single<VisualDiffRunner> {
                object : VisualDiffRunner {
                    override fun run(
                        baseCommand: String,
                        oldFile: String,
                        newFile: String,
                        outputDir: Path,
                    ): CommandResult {
                        return CommandResult(exitCode = 0, stdout = "", stderr = "")
                    }
                }
            }
        }
    }

    private fun extractField(json: String, field: String): String {
        val pattern = Regex("\"$field\":\"([^\"]+)\"")
        return pattern.find(json)?.groupValues?.get(1) ?: error("field '$field' not found in $json")
    }
}
