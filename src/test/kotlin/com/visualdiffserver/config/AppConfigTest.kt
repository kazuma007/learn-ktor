package com.visualdiffserver.config

import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class AppConfigTest {
    @Test
    fun fallsBackToProjectLocalPathsForDockerDefaultsWhenRunningLocally() {
        val workingDir = createTempDirectory("app-config-local")
        workingDir.resolve("visualdiff").createDirectories()
        workingDir.resolve("visualdiff/visualdiff.jar").writeText("jar")

        val config = AppConfig.fromEnv(
            environment = mapOf(
                "DB_URL" to "jdbc:postgresql://localhost:5432/diffdb",
                "DB_USER" to "diff",
                "DB_PASSWORD" to "diff",
                "DATA_DIR" to "/data",
                "VISUAL_DIFF_CMD" to "java -jar /app/visualdiff/visualdiff.jar",
            ),
            workingDir = workingDir,
        )

        assertEquals(workingDir.resolve("data").normalize(), config.dataDir)
        assertEquals("java -jar ./visualdiff/visualdiff.jar", config.visualDiffCmd)
    }

    @Test
    fun keepsExplicitLocalValuesUnchanged() {
        val workingDir = createTempDirectory("app-config-explicit")

        val config = AppConfig.fromEnv(
            environment = mapOf(
                "DB_URL" to "jdbc:postgresql://localhost:5432/diffdb",
                "DB_USER" to "diff",
                "DB_PASSWORD" to "diff",
                "DATA_DIR" to "./tmp/data",
                "VISUAL_DIFF_CMD" to "java -jar ./tools/visualdiff.jar",
            ),
            workingDir = workingDir,
        )

        assertEquals(Path("./tmp/data"), config.dataDir)
        assertEquals("java -jar ./tools/visualdiff.jar", config.visualDiffCmd)
    }
}
