package com.visualdiffserver

import com.visualdiffserver.app.productionModule
import com.visualdiffserver.config.AppConfig
import com.visualdiffserver.http.configureRouting
import com.visualdiffserver.http.plugins.configureSerialization
import com.visualdiffserver.http.plugins.configureStatusPages
import com.visualdiffserver.persistence.DatabaseFactory
import com.visualdiffserver.persistence.DiffRepository
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
    configureRouting()

    val config by inject<AppConfig>()
    val storage by inject<StorageService>()
    val repository by inject<DiffRepository>()
    val visualDiffRunner by inject<VisualDiffRunner>()

    if (initializeDatabase) {
        DatabaseFactory.init(config)
    }

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
