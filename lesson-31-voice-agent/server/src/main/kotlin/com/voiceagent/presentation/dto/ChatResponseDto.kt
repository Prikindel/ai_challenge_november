package com.voiceagent.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для HTTP ответа клиенту
 */
@Serializable
data class ChatResponseDto(
    val response: String
)

@Serializable
data class ErrorResponseDto(
    val error: String
)

