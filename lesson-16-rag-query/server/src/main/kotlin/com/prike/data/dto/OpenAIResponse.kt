package com.prike.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO для ответа от OpenAI API (OpenRouter)
 */
@Serializable
data class OpenAIResponse(
    val choices: List<ChoiceDto>,
    val usage: UsageDto? = null
)

/**
 * DTO для варианта ответа
 */
@Serializable
data class ChoiceDto(
    val message: MessageDto,
    @SerialName("finish_reason") val finishReason: String? = null
)

/**
 * DTO для использования токенов
 */
@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null
)

/**
 * DTO для ошибки от API
 */
@Serializable
data class OpenAIErrorResponse(
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

