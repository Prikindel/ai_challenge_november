package com.prike.presentation.dto

import kotlinx.serialization.Serializable

@Serializable
data class TemperatureRequestDto(
    val question: String? = null,
    val temperatures: List<Double>? = null
)

@Serializable
data class TemperatureResponseDto(
    val defaultQuestion: String,
    val defaultTemperatures: List<Double>,
    val question: String,
    val results: List<TemperatureResultDto>,
    val comparison: TemperatureComparisonDto
)

@Serializable
data class TemperatureResultDto(
    val mode: String,
    val temperature: Double,
    val answer: String,
    val meta: TemperatureMetaDto
)

@Serializable
data class TemperatureMetaDto(
    val durationMs: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val requestJson: String? = null,
    val responseJson: String? = null
)

@Serializable
data class TemperatureDefaultsDto(
    val defaultQuestion: String,
    val defaultTemperatures: List<Double>
)

@Serializable
data class TemperatureComparisonDto(
    val summary: String,
    val perTemperature: List<TemperatureRecommendationDto>
)

@Serializable
data class TemperatureRecommendationDto(
    val temperature: Double,
    val mode: String,
    val accuracy: String,
    val creativity: String,
    val diversity: String,
    val recommendation: String
)

