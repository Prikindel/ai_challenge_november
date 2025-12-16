package com.voiceagent.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для HTTP запроса от клиента
 */
@Serializable
data class ChatRequestDto(
    val message: String
)

