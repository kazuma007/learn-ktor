package com.visualdiffserver.plugins

import com.visualdiffserver.config.AppConfig
import com.visualdiffserver.infrastructure.db.DatabaseFactory
import io.ktor.server.application.Application
import org.koin.ktor.ext.inject

fun Application.configureDatabase() {
    val config by inject<AppConfig>()
    DatabaseFactory.init(config)
}
