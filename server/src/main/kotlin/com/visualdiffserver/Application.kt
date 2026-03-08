package com.visualdiffserver

import com.visualdiffserver.http.configureRouting
import com.visualdiffserver.persistence.DatabaseFactory
import com.visualdiffserver.http.plugins.configureSerialization
import com.visualdiffserver.http.plugins.configureStatusPages
import com.visualdiffserver.app.AppDependencies
import com.visualdiffserver.worker.RunWorker
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

fun Application.module(dependencies: AppDependencies = AppDependencies.fromEnv()) {
    configureSerialization()
    configureStatusPages()

    if (dependencies.initializeDatabase) {
        DatabaseFactory.init(dependencies.config)
    }
    configureRouting(
        repository = dependencies.repository,
        storage = dependencies.storage,
    )

    val worker = RunWorker(
        logger = log,
        config = dependencies.config,
        storage = dependencies.storage,
        repository = dependencies.repository,
        visualDiffRunner = dependencies.visualDiffRunner,
    )
    worker.start()

    monitor.subscribe(ApplicationStopped) {
        worker.stop()
    }
}
