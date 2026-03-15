package com.visualdiffserver.application

import com.visualdiffserver.config.AppConfig
import com.visualdiffserver.domain.DiffRepository
import com.visualdiffserver.infrastructure.db.repository.ExposedDiffRepository
import com.visualdiffserver.storage.StorageService
import com.visualdiffserver.worker.ShellVisualDiffRunner
import com.visualdiffserver.worker.VisualDiffRunner
import org.koin.core.module.Module
import org.koin.dsl.module

fun productionModule(config: AppConfig = AppConfig.fromEnv()): Module = module {
    single { config }
    single { StorageService(get()) }
    single<DiffRepository> { ExposedDiffRepository() }
    single { DiffService(get(), get()) }
    single<VisualDiffRunner> { ShellVisualDiffRunner() }
}
