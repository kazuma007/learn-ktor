package com.visualdiffserver

import com.visualdiffserver.application.productionModule
import com.visualdiffserver.config.AppConfig
import com.visualdiffserver.domain.RunQueueRepository
import com.visualdiffserver.plugins.configureDatabase
import com.visualdiffserver.plugins.configureRouting
import com.visualdiffserver.plugins.configureSerialization
import com.visualdiffserver.plugins.configureStatusPages
import com.visualdiffserver.storage.StorageService
import com.visualdiffserver.worker.RunWorker
import com.visualdiffserver.worker.VisualDiffRunner
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.koin.core.module.Module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

fun Application.module(
    initializeDatabase: Boolean = true,
    rootModule: Module = productionModule(),
    extraModules: List<Module> = emptyList(),
) {
    install(Koin) { modules(listOf(rootModule) + extraModules) }

    configureSerialization()
    configureStatusPages()
    if (initializeDatabase) {
        configureDatabase()
    }
    configureRouting()

    val config by inject<AppConfig>()
    val storage by inject<StorageService>()
    val repository by inject<RunQueueRepository>()
    val visualDiffRunner by inject<VisualDiffRunner>()

    val worker =
        RunWorker(
            logger = log,
            config = config,
            storage = storage,
            repository = repository,
            visualDiffRunner = visualDiffRunner,
        )
    worker.start()

    monitor.subscribe(ApplicationStopped) { worker.stop() }
}
