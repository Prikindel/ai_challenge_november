package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для HTTP ответа с ошибкой
 */
@Serializable
data class ErrorResponseDto(
    val error: String
)

