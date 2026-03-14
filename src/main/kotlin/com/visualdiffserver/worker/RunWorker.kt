package com.visualdiffserver.worker

import com.visualdiffserver.config.AppConfig
import com.visualdiffserver.domain.ArtifactKind
import com.visualdiffserver.persistence.DiffRepository
import com.visualdiffserver.storage.StorageService
import kotlin.io.path.Path as toPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.Logger

class RunWorker(
    private val logger: Logger,
    private val config: AppConfig,
    private val storage: StorageService,
    private val repository: DiffRepository,
    private val visualDiffRunner: VisualDiffRunner,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var workerJob: Job? = null

    fun start() {
        if (workerJob?.isActive == true) return

        workerJob =
            scope.launch {
                logger.info("Run worker started")
                while (isActive) {
                    try {
                        processNextRunOnce()
                    } catch (e: Exception) {
                        logger.error("Run worker loop failed", e)
                    }
                    delay(POLL_INTERVAL_MS)
                }
            }
    }

    fun stop() {
        workerJob?.cancel()
    }

    suspend fun processNextRunOnce() {
        val job = repository.claimNextQueuedRun() ?: return
        val outputDir = toPath(job.outputDir)
        outputDir.toFile().mkdirs()

        logger.info("Processing run {}", job.runId)

        try {
            val result =
                visualDiffRunner.run(
                    baseCommand = config.visualDiffCmd,
                    oldFile = job.oldFilePath,
                    newFile = job.newFilePath,
                    outputDir = outputDir,
                )

            if (result.exitCode != 0) {
                repository.saveRunResultFailure(
                    runId = job.runId,
                    exitCode = result.exitCode,
                    stdoutText = result.stdout,
                    stderrText = result.stderr,
                    errorText = "visual-diff failed with exit code ${result.exitCode}",
                )
                return
            }

            val artifacts = storage.scanArtifacts(outputDir)
            if (artifacts.none { it.kind == ArtifactKind.REPORT_HTML }) {
                repository.saveRunResultFailure(
                    runId = job.runId,
                    exitCode = result.exitCode,
                    stdoutText = result.stdout,
                    stderrText = result.stderr,
                    errorText = "report.html was not generated",
                )
                return
            }

            repository.saveRunResultSuccess(
                runId = job.runId,
                exitCode = result.exitCode,
                stdoutText = result.stdout,
                stderrText = result.stderr,
                artifacts = artifacts,
            )
        } catch (e: Exception) {
            repository.saveRunResultFailure(
                runId = job.runId,
                exitCode = null,
                stdoutText = null,
                stderrText = null,
                errorText = e.message ?: "Unknown worker error",
            )
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
    }
}
