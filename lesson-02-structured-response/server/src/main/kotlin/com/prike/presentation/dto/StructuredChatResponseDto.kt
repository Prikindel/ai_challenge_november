package com.prike.presentation.dto

import com.prike.data.dto.ChatStructuredResponse
import kotlinx.serialization.Serializable

/**
 * DTO для HTTP ответа со структурированными данными от AI
 */
@Serializable
data class StructuredChatResponseDto(
    val structuredResponse: ChatStructuredResponse
)

