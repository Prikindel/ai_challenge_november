package com.prike.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<MessageDto>,
    val temperature: Double,
    @SerialName("max_tokens")
    val maxTokens: Int?,
    @SerialName("response_format")
    val responseFormat: ResponseFormatDto? = null
)

@Serializable
data class MessageDto(
    val role: String,
    val content: String,
    val name: String? = null
)

@Serializable
data class ResponseFormatDto(
    val type: String
)

