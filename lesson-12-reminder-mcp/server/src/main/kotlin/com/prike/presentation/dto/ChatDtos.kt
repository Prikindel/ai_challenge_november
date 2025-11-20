package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для запроса чата
 */
@Serializable
data class ChatRequestDto(
    val message: String
)

/**
 * DTO для ответа чата
 */
@Serializable
data class ChatResponseDto(
    val success: Boolean,
    val message: String? = null,
    val toolUsed: String? = null,
    val error: String? = null
)

