package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для запроса от клиента
 * Содержит только примитивы, не зависит от LLM DTO
 */
@Serializable
data class ChatRequestDto(
    val message: String
)

