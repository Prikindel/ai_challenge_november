package com.prike.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class OpenAIResponse(
    val choices: List<ChoiceDto>
)

@Serializable
data class ChoiceDto(
    val message: MessageDto
)

