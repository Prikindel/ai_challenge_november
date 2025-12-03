package com.prike.mcpcommon.dto

import kotlinx.serialization.Serializable

/**
 * DTO для сообщений в диалоге с LLM
 */
@Serializable
data class MessageDto(
    val role: String, // "system", "user", "assistant"
    val content: String
)

