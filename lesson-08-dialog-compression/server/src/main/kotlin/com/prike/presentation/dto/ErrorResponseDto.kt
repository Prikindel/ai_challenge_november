package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для ошибки клиенту
 * Содержит только примитивы, не зависит от LLM DTO
 */
@Serializable
data class ErrorResponseDto(
    val error: String
)

