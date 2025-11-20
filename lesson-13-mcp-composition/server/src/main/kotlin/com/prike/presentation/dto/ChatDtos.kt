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
    val toolCalls: List<ToolCallDto> = emptyList()
)

/**
 * DTO для информации о вызове инструмента
 */
@Serializable
data class ToolCallDto(
    val name: String,
    val success: Boolean
)

/**
 * DTO для ошибки
 */
@Serializable
data class ErrorDto(
    val message: String
)

