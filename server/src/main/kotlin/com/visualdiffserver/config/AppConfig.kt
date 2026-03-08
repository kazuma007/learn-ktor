package com.visualdiffserver.config

import java.nio.file.Path
import kotlin.io.path.Path

private const val DEFAULT_DATA_DIR = "/data"
private const val DEFAULT_VISUAL_DIFF_CMD = "java -jar ./visualdiff/visualdiff.jar"

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
        fun fromEnv(): AppConfig {
            val dbUrl = requireEnv("DB_URL")
            val dbUser = requireEnv("DB_USER")
            val dbPassword = requireEnv("DB_PASSWORD")
            val dataDir = Path(System.getenv("DATA_DIR") ?: DEFAULT_DATA_DIR)
            val visualDiffCmd = System.getenv("VISUAL_DIFF_CMD") ?: DEFAULT_VISUAL_DIFF_CMD

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

        private fun requireEnv(name: String): String {
            return System.getenv(name)
                ?: throw IllegalStateException("Environment variable $name is required")
        }
    }
}
