package com.visualdiffserver.domain

import kotlinx.serialization.Serializable

@Serializable data class ErrorResponse(val error: String)
