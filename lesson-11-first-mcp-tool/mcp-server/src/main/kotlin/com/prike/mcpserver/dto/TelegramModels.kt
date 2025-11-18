package com.prike.mcpserver.dto

import kotlinx.serialization.Serializable

@Serializable
data class TelegramUser(
    val id: Long,
    val isBot: Boolean,
    val firstName: String,
    val username: String? = null,
    val languageCode: String? = null
)

@Serializable
data class TelegramChat(
    val id: Long,
    val type: String,
    val title: String? = null,
    val username: String? = null,
    val firstName: String? = null,
    val lastName: String? = null
)

@Serializable
data class TelegramMessage(
    val messageId: Long,
    val from: TelegramUser? = null,
    val chat: TelegramChat,
    val date: Long,
    val text: String? = null
)

@Serializable
data class GetMeResponse(
    val ok: Boolean,
    val result: TelegramUser
)

@Serializable
data class SendMessageResponse(
    val ok: Boolean,
    val result: TelegramMessage
)

@Serializable
data class TelegramErrorResponse(
    val ok: Boolean,
    val errorCode: Int,
    val description: String
)

