package com.prike.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO для ответа от OpenAI API
 */
@Serializable
data class OpenAIResponse(
    val choices: List<ChoiceDto>
)

/**
 * DTO для варианта ответа
 */
@Serializable
data class ChoiceDto(
    val message: MessageDto,
    @SerialName("finish_reason") val finishReason: String? = null
)

