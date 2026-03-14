package com.visualdiffserver.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import java.util.UUID

fun ApplicationCall.requireUuidPathParam(name: String): UUID {
    val raw = parameters[name]
    return raw.toUuidOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "invalid $name")
}

fun String?.toUuidOrNull(): UUID? {
    if (this == null) return null
    return try {
        UUID.fromString(this)
    } catch (_: IllegalArgumentException) {
        null
    }
}
