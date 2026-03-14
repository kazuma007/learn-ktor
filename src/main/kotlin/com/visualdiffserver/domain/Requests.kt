package com.visualdiffserver.domain

import kotlinx.serialization.Serializable

@Serializable
data class CreateProjectRequest(val name: String)

@Serializable
data class CreateComparisonRequest(
    val oldAssetId: String,
    val newAssetId: String,
)
