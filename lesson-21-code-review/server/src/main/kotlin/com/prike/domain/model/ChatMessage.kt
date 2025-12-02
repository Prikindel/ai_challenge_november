package com.prike.domain.model

/**
 * Сообщение в чате
 */
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val citations: List<Citation> = emptyList(),
    val createdAt: Long
)

/**
 * Роль сообщения
 */
enum class MessageRole {
    USER,
    ASSISTANT
}

