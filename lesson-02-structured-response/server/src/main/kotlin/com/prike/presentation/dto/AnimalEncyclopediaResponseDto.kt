package com.prike.presentation.dto

import com.prike.data.dto.AnimalEncyclopediaResponse
import kotlinx.serialization.Serializable

/**
 * Результат обработки запроса с debug информацией
 */
data class ChatResponseResult(
    val response: AnimalEncyclopediaResponse,
    val llmRequestJson: String,
    val llmResponseJson: String
)

/**
 * DTO для HTTP ответа энциклопедии животных
 */
@Serializable
data class AnimalEncyclopediaResponseDto(
    val response: AnimalEncyclopediaResponse,
    val debug: DebugInfo? = null
) {
    @Serializable
    data class DebugInfo(
        val llmRequest: String,
        val llmResponse: String
    )
}

