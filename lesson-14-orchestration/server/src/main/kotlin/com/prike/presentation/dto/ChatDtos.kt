package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для запроса чата
 */
@Serializable
data class ChatMessageRequestDto(
    val message: String
)

/**
 * DTO для ответа чата
 */
@Serializable
data class ChatMessageResponseDto(
    val message: String,
    val toolCalls: List<ToolCallInfoDto> = emptyList(),
    val processingTime: Long = 0  // в миллисекундах
)

/**
 * DTO для информации о вызове инструмента
 */
@Serializable
data class ToolCallInfoDto(
    val toolName: String,
    val serverId: String? = null,
    val success: Boolean,
    val result: String? = null
)

/**
 * DTO для ошибки
 */
@Serializable
data class ErrorDto(
    val message: String
)

