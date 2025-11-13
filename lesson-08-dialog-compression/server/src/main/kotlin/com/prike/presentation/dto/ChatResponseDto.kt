package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для ответа клиенту
 * Содержит только примитивы, не зависит от LLM DTO
 */
@Serializable
data class ChatResponseDto(
    val message: String
)

