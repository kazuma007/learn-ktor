package com.visualdiffserver.routes

import com.visualdiffserver.application.DiffService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.jvm.javaio.toInputStream

suspend fun ApplicationCall.receiveAssetUpload(): DiffService.AssetUpload {
    val multipart = receiveMultipart()

    while (true) {
        val part = multipart.readPart() ?: break
        try {
            if (part is PartData.FileItem) {
                return DiffService.AssetUpload(
                    originalFilename = part.originalFileName ?: "upload.bin",
                    contentType = part.contentType?.toString() ?: "application/octet-stream",
                    data = part.provider().toInputStream(),
                )
            }
        } finally {
            if (part !is PartData.FileItem) {
                part.dispose()
            }
        }
    }

    throw ApiException(HttpStatusCode.BadRequest, "file part is required")
}
