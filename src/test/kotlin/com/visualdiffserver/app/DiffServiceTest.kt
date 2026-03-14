package com.visualdiffserver.app

import com.visualdiffserver.config.AppConfig
import com.visualdiffserver.http.ApiException
import com.visualdiffserver.support.FakeDiffRepository
import com.visualdiffserver.storage.StorageService
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiffServiceTest {
    @Test
    fun createProjectRejectsBlankName() = runBlocking {
        val service = testService()

        val error = assertFailsWith<ApiException> {
            service.createProject("   ")
        }

        assertEquals("name must not be blank", error.message)
    }

    @Test
    fun createAssetRejectsMissingProject() = runBlocking {
        val service = testService()

        val error = assertFailsWith<ApiException> {
            service.createAsset(
                projectId = UUID.randomUUID(),
                upload = DiffService.AssetUpload(
                    originalFilename = "sample.pdf",
                    contentType = "application/pdf",
                    data = ByteArrayInputStream("pdf".encodeToByteArray()),
                ),
            )
        }

        assertEquals("project not found", error.message)
    }

    @Test
    fun createComparisonRejectsAssetsFromAnotherProject() = runBlocking {
        val fixture = testFixture()
        val projectOne = fixture.repository.createProject("one")
        val projectTwo = fixture.repository.createProject("two")
        val projectOneId = UUID.fromString(projectOne.id)
        val projectTwoId = UUID.fromString(projectTwo.id)

        val oldAsset = fixture.repository.createAsset(
            projectOneId,
            StorageService.StoredFile("old.pdf", "application/pdf", 1, "old", "/tmp/old.pdf"),
        )
        val newAsset = fixture.repository.createAsset(
            projectTwoId,
            StorageService.StoredFile("new.pdf", "application/pdf", 1, "new", "/tmp/new.pdf"),
        )

        val error = assertFailsWith<ApiException> {
            fixture.service.createComparison(
                projectId = projectOneId,
                oldAssetId = UUID.fromString(oldAsset.id),
                newAssetId = UUID.fromString(newAsset.id),
            )
        }

        assertEquals("assets must belong to project", error.message)
    }

    @Test
    fun getAssetDownloadRejectsMissingStoredFile() = runBlocking {
        val fixture = testFixture()
        val project = fixture.repository.createProject("demo")
        val projectId = UUID.fromString(project.id)
        val asset = fixture.repository.createAsset(
            projectId,
            StorageService.StoredFile("missing.pdf", "application/pdf", 1, "sha", "/tmp/definitely-missing.pdf"),
        )

        val error = assertFailsWith<ApiException> {
            fixture.service.getAssetDownload(UUID.fromString(asset.id))
        }

        assertEquals("stored file not found", error.message)
    }

    @Test
    fun createRunCreatesOutputDirectoryInsideConfiguredRunsDir() = runBlocking {
        val fixture = testFixture()
        val project = fixture.repository.createProject("demo")
        val projectId = UUID.fromString(project.id)

        val oldAsset = fixture.service.createAsset(
            projectId,
            DiffService.AssetUpload(
                originalFilename = "old.pdf",
                contentType = "application/pdf",
                data = ByteArrayInputStream("old".encodeToByteArray()),
            ),
        )
        val newAsset = fixture.service.createAsset(
            projectId,
            DiffService.AssetUpload(
                originalFilename = "new.pdf",
                contentType = "application/pdf",
                data = ByteArrayInputStream("new".encodeToByteArray()),
            ),
        )
        val comparison = fixture.service.createComparison(
            projectId = projectId,
            oldAssetId = UUID.fromString(oldAsset.id),
            newAssetId = UUID.fromString(newAsset.id),
        )

        val run = fixture.service.createRun(UUID.fromString(comparison.id))

        assertTrue(run.outputDir.startsWith(fixture.config.runsDir.toAbsolutePath().toString()))
    }

    @Test
    fun getReportDownloadRejectsMissingReportArtifact() = runBlocking {
        val service = testService()

        val error = assertFailsWith<ApiException> {
            service.getReportDownload(UUID.randomUUID())
        }

        assertEquals("report not found", error.message)
    }

    private fun testService(): DiffService = testFixture().service

    private fun testFixture(): TestFixture {
        val dataRoot = createTempDirectory("diff-service-test")
        val config = AppConfig(
            dbUrl = "jdbc:postgresql://unused",
            dbUser = "unused",
            dbPassword = "unused",
            dataDir = dataRoot,
            assetsDir = dataRoot.resolve("assets").createDirectories(),
            runsDir = dataRoot.resolve("runs").createDirectories(),
            visualDiffCmd = "echo",
        )
        val storage = StorageService(config)
        val repository = FakeDiffRepository()
        return TestFixture(
            config = config,
            service = DiffService(repository, storage),
            repository = repository,
        )
    }

    private data class TestFixture(
        val config: AppConfig,
        val service: DiffService,
        val repository: FakeDiffRepository,
    )
}
