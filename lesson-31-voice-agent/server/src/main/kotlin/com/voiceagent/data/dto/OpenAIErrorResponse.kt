package com.voiceagent.data.dto

import kotlinx.serialization.Serializable

/**
 * DTO для ошибок OpenAI API
 */
@Serializable
data class OpenAIErrorResponse(
    val error: OpenAIError? = null
)

@Serializable
data class OpenAIError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

