package com.prike.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO для запроса к OpenAI API
 */
@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<MessageDto>,
    val temperature: Double,
    @SerialName("max_tokens") val maxTokens: Int,
    val tools: List<ToolDto>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null // "auto", "none", или объект с конкретным инструментом
)

