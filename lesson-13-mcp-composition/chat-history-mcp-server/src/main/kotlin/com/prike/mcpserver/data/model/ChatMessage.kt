package com.prike.mcpserver.data.model

/**
 * Модель сообщения из веб-чата
 */
data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val model: String? = null
)

