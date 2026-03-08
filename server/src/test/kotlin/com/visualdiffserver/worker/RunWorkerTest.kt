package com.visualdiffserver.worker

import com.visualdiffserver.config.AppConfig
import com.visualdiffserver.domain.RunStatus
import com.visualdiffserver.storage.StorageService
import com.visualdiffserver.support.FakeDiffRepository
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RunWorkerTest {
    @Test
    fun workerMarksRunSucceededAndRegistersArtifacts() = runBlocking {
        val dataRoot = createTempDirectory("worker-test-data")
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

        val oldFile = Files.createTempFile("old", ".pdf").toAbsolutePath().toString()
        val newFile = Files.createTempFile("new", ".pdf").toAbsolutePath().toString()
        val outputDir = config.runsDir.resolve("run-1").createDirectories().toAbsolutePath().toString()
        val runId = repository.enqueueRun(oldFile, newFile, outputDir)

        val runner = object : VisualDiffRunner {
            override fun run(
                baseCommand: String,
                oldFile: String,
                newFile: String,
                outputDir: java.nio.file.Path,
            ): CommandResult {
                outputDir.resolve("report.html").writeText("<html>ok</html>")
                outputDir.resolve("diff.json").writeText("{}")
                outputDir.resolve("delta.png").writeText("png")
                outputDir.resolve("report.css").writeText("body{}")
                outputDir.resolve("report.js").writeText("console.log('ok')")
                return CommandResult(exitCode = 0, stdout = "done", stderr = "")
            }
        }

        val worker = RunWorker(
            logger = LoggerFactory.getLogger("RunWorkerTest"),
            config = config,
            storage = storage,
            repository = repository,
            visualDiffRunner = runner,
        )

        worker.processNextRunOnce()

        val run = repository.getRun(runId)
        assertNotNull(run)
        assertEquals(RunStatus.SUCCEEDED.name, run.status)
        assertEquals(5, repository.listArtifacts(runId).size)
    }
}
