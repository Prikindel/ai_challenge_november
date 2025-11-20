package com.prike.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO для сообщений в диалоге с LLM
 */
@Serializable
data class MessageDto(
    val role: String, // "system", "user", "assistant", "tool"
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null
)

