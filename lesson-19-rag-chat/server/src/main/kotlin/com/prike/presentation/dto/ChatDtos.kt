package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для создания сессии
 */
@Serializable
data class CreateSessionRequest(
    val title: String? = null
)

/**
 * DTO для сессии чата
 */
@Serializable
data class ChatSessionDto(
    val id: String,
    val title: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * DTO для отправки сообщения
 */
@Serializable
data class SendMessageRequest(
    val message: String,
    val topK: Int = 5,
    val minSimilarity: Float = 0.4f,
    val applyFilter: Boolean? = null,
    val strategy: String? = null
)

/**
 * DTO для цитаты в сообщении
 */
@Serializable
data class MessageCitationDto(
    val text: String,
    val documentPath: String,
    val documentTitle: String,
    val chunkId: String? = null
)

/**
 * DTO для сообщения в чате
 */
@Serializable
data class ChatMessageDto(
    val id: String,
    val sessionId: String,
    val role: String,  // "USER" или "ASSISTANT"
    val content: String,
    val citations: List<MessageCitationDto> = emptyList(),
    val createdAt: Long
)

/**
 * DTO для ответа с историей сообщений
 */
@Serializable
data class ChatHistoryResponse(
    val sessionId: String,
    val messages: List<ChatMessageDto>
)

/**
 * DTO для ответа на отправку сообщения
 */
@Serializable
data class SendMessageResponse(
    val message: ChatMessageDto,
    val sessionId: String
)

