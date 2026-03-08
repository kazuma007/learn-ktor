package com.visualdiffserver.app

import com.visualdiffserver.config.AppConfig
import com.visualdiffserver.persistence.DiffRepository
import com.visualdiffserver.persistence.ExposedDiffRepository
import com.visualdiffserver.storage.StorageService
import com.visualdiffserver.worker.ShellVisualDiffRunner
import com.visualdiffserver.worker.VisualDiffRunner

data class AppDependencies(
    val config: AppConfig,
    val storage: StorageService,
    val repository: DiffRepository,
    val visualDiffRunner: VisualDiffRunner,
    val initializeDatabase: Boolean,
) {
    companion object {
        fun fromEnv(): AppDependencies {
            val config = AppConfig.fromEnv()
            return AppDependencies(
                config = config,
                storage = StorageService(config),
                repository = ExposedDiffRepository(),
                visualDiffRunner = ShellVisualDiffRunner(),
                initializeDatabase = true,
            )
        }
    }
}
