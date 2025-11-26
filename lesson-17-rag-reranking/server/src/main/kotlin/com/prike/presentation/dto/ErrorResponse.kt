package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * Общий DTO для ошибок API
 */
@Serializable
data class ErrorResponse(
    val error: String
)

