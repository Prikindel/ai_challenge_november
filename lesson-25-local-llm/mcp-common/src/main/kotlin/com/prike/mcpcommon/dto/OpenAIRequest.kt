package com.prike.mcpcommon.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DTO для запроса к OpenAI API (OpenRouter)
 */
@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<MessageDto>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 2000,
    val tools: List<JsonObject>? = null,  // Используем JsonObject для гибкости
    @SerialName("response_format") val responseFormat: JsonObject? = null  // Для JSON mode
)

