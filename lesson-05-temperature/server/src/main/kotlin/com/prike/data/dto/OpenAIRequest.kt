package com.prike.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<MessageDto>,
    val temperature: Double,
    val maxTokens: Int,
    val responseFormat: JsonObject? = null // {"type": "json_object"} для JSON режима
)

@Serializable
data class MessageDto(
    val role: String,
    val content: String
)

