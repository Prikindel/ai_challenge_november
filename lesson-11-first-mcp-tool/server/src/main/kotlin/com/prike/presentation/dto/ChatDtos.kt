package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для запроса сообщения в чат
 */
@Serializable
data class ChatMessageRequestDto(
    val message: String
)

/**
 * DTO для ответа на сообщение в чате
 */
@Serializable
data class ChatMessageResponseDto(
    val message: String,
    val toolUsed: String? = null,
    val toolResult: String? = null
)

