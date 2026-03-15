package com.visualdiffserver.plugins

import com.visualdiffserver.routes.configureApiRoutes
import io.ktor.server.application.Application

fun Application.configureRouting() {
    configureApiRoutes()
}
