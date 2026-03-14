package com.visualdiffserver.worker

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

interface VisualDiffRunner {
    fun run(baseCommand: String, oldFile: String, newFile: String, outputDir: Path): CommandResult
}

data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String)

class ShellVisualDiffRunner : VisualDiffRunner {
    override fun run(
        baseCommand: String,
        oldFile: String,
        newFile: String,
        outputDir: Path,
    ): CommandResult {
        val command = buildCommand(baseCommand, oldFile, newFile, outputDir)
        val process =
            ProcessBuilder("sh", "-c", command)
                .directory(File(System.getProperty("user.dir")))
                .start()

        val stdoutCapture = captureStream(process.inputStream)
        val stderrCapture = captureStream(process.errorStream)

        val exitCode = process.waitFor()
        return CommandResult(
            exitCode = exitCode,
            stdout = stdoutCapture.await(),
            stderr = stderrCapture.await(),
        )
    }

    private fun buildCommand(
        base: String,
        oldFile: String,
        newFile: String,
        outputDir: Path,
    ): String {
        return "$base --old-file ${shellEscape(oldFile)} --new-file ${shellEscape(newFile)} --out ${shellEscape(outputDir.toString())}"
    }

    private fun shellEscape(raw: String): String {
        return "'" + raw.replace("'", "'\\''") + "'"
    }

    private data class StreamCapture(
        private val latch: CountDownLatch,
        private val output: StringBuilder,
    ) {
        fun await(): String {
            latch.await()
            return output.toString()
        }
    }

    private fun captureStream(stream: InputStream): StreamCapture {
        val output = StringBuilder()
        val latch = CountDownLatch(1)
        thread(start = true, isDaemon = true) {
            try {
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        output.append(line).append('\n')
                        line = reader.readLine()
                    }
                }
            } finally {
                latch.countDown()
            }
        }
        return StreamCapture(latch, output)
    }
}
