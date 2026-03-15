package com.visualdiffserver.api.response

import kotlinx.serialization.Serializable

@Serializable data class ErrorResponse(val error: String)
