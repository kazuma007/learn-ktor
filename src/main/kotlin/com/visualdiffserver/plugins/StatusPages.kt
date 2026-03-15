package com.visualdiffserver.plugins

import com.visualdiffserver.api.response.ErrorResponse
import com.visualdiffserver.routes.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.statusCode, ErrorResponse(cause.message))
        }

        exception<Throwable> { call, cause ->
            this@configureStatusPages.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal server error"))
        }
    }
}
