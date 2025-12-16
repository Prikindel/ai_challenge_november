package com.voiceagent.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<MessageDto>,
    val temperature: Double,
    val maxTokens: Int
)

@Serializable
data class MessageDto(
    val role: String,
    val content: String
)

