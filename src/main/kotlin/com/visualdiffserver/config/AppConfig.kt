package com.visualdiffserver.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

private const val DEFAULT_LOCAL_DATA_DIR = "./data"
private const val CONTAINER_DATA_DIR = "/data"
private const val DEFAULT_LOCAL_VISUAL_DIFF_CMD = "java -jar ./visualdiff/visualdiff.jar"
private const val CONTAINER_VISUAL_DIFF_CMD = "java -jar /app/visualdiff/visualdiff.jar"

data class AppConfig(
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val dataDir: Path,
    val assetsDir: Path,
    val runsDir: Path,
    val visualDiffCmd: String,
) {
    companion object {
        fun fromEnv(
            environment: Map<String, String> = System.getenv(),
            workingDir: Path = Path(System.getProperty("user.dir")),
        ): AppConfig {
            val dbUrl = requireEnv(environment, "DB_URL")
            val dbUser = requireEnv(environment, "DB_USER")
            val dbPassword = requireEnv(environment, "DB_PASSWORD")
            val dataDir = resolveDataDir(environment["DATA_DIR"], workingDir)
            val visualDiffCmd = resolveVisualDiffCmd(environment["VISUAL_DIFF_CMD"], workingDir)

            return AppConfig(
                dbUrl = dbUrl,
                dbUser = dbUser,
                dbPassword = dbPassword,
                dataDir = dataDir,
                assetsDir = dataDir.resolve("assets"),
                runsDir = dataDir.resolve("runs"),
                visualDiffCmd = visualDiffCmd,
            )
        }

        private fun requireEnv(environment: Map<String, String>, name: String): String {
            return environment[name]
                ?: throw IllegalStateException("Environment variable $name is required")
        }

        private fun resolveDataDir(rawDataDir: String?, workingDir: Path): Path {
            val configured = Path(rawDataDir ?: DEFAULT_LOCAL_DATA_DIR)
            if (rawDataDir == CONTAINER_DATA_DIR && !canCreateDirectory(configured)) {
                return workingDir.resolve("data").normalize()
            }
            return configured
        }

        private fun resolveVisualDiffCmd(rawCommand: String?, workingDir: Path): String {
            val configured = rawCommand ?: DEFAULT_LOCAL_VISUAL_DIFF_CMD
            if (configured != CONTAINER_VISUAL_DIFF_CMD) {
                return configured
            }

            val containerJar = Path("/app/visualdiff/visualdiff.jar")
            if (containerJar.exists()) {
                return configured
            }

            val localJar = workingDir.resolve("visualdiff/visualdiff.jar")
            return if (localJar.exists()) DEFAULT_LOCAL_VISUAL_DIFF_CMD else configured
        }

        private fun canCreateDirectory(path: Path): Boolean {
            if (path.exists()) {
                return Files.isWritable(path)
            }

            val parent = path.toAbsolutePath().parent ?: return false
            return parent.exists() && Files.isWritable(parent)
        }
    }
}
