package com.visualdiffserver.domain

import com.visualdiffserver.storage.StorageService
import java.util.UUID

interface RunQueueRepository {
    suspend fun claimNextQueuedRun(): QueuedRunWork?

    suspend fun saveRunResultSuccess(
        runId: UUID,
        exitCode: Int,
        stdoutText: String,
        stderrText: String,
        artifacts: List<StorageService.ScannedArtifact>,
    )

    suspend fun saveRunResultFailure(
        runId: UUID,
        exitCode: Int?,
        stdoutText: String?,
        stderrText: String?,
        errorText: String,
    )
}
