package com.prike.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO для запроса к OpenAI API (OpenRouter)
 */
@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<MessageDto>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 2000,
    val tools: List<ToolDto>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null
)

