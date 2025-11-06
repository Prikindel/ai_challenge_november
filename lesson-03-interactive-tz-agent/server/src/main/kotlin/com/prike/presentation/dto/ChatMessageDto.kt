package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для сообщения в чат
 */
@Serializable
data class ChatMessageDto(
    val message: String
)

