package com.prike.presentation.dto

import kotlinx.serialization.Serializable

@Serializable
data class ModelsCatalogDto(
    val defaultQuestion: String,
    val defaultModelIds: List<String>,
    val models: List<ModelInfoDto>
)

@Serializable
data class ModelInfoDto(
    val id: String,
    val displayName: String,
    val endpoint: String,
    val huggingFaceUrl: String,
    val pricePer1kTokensUsd: Double? = null,
    val defaultParams: Map<String, String?> = emptyMap()
)

@Serializable
data class ModelComparisonRequestDto(
    val question: String? = null,
    val modelIds: List<String>? = null,
    val includeComparison: Boolean? = null
)

@Serializable
data class ModelComparisonResponseDto(
    val defaultQuestion: String,
    val defaultModelIds: List<String>,
    val question: String,
    val modelResults: List<ModelResultDto>,
    val comparisonSummary: String,
    val modelLinks: List<ModelLinkDto>,
    val comparisonEnabled: Boolean
)

@Serializable
data class ModelResultDto(
    val modelId: String,
    val displayName: String,
    val huggingFaceUrl: String,
    val answer: String,
    val isError: Boolean,
    val meta: ModelMetaDto
)

@Serializable
data class ModelMetaDto(
    val durationMs: Long?,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val costUsd: Double?
)

@Serializable
data class ModelLinkDto(
    val modelId: String,
    val huggingFaceUrl: String
)

