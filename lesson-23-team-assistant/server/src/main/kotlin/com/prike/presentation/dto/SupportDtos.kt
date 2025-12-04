package com.prike.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTO для запроса на поддержку
 */
@Serializable
data class SupportQuestionRequest(
    val ticketId: String? = null,
    val userId: String? = null,
    val question: String
)

/**
 * DTO для источника информации
 */
@Serializable
data class SourceDto(
    val title: String,
    val content: String,
    val url: String? = null
)

/**
 * DTO для ответа на вопрос поддержки
 */
@Serializable
data class SupportQuestionResponse(
    val answer: String,
    val sources: List<SourceDto>,
    val suggestions: List<String>? = null,
    val ticketId: String? = null,
    val shouldCreateTicket: Boolean = false
)

/**
 * DTO для создания тикета
 */
@Serializable
data class CreateTicketRequest(
    val userId: String,
    val subject: String,
    val description: String
)

/**
 * DTO для тикета
 */
@Serializable
data class TicketDto(
    val id: String,
    val userId: String,
    val subject: String,
    val description: String,
    val status: String,
    val priority: String,
    val messageCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * DTO для сообщения в тикете
 */
@Serializable
data class TicketMessageDto(
    val id: String,
    val ticketId: String,
    val author: String,
    val content: String,
    val timestamp: Long
)

/**
 * DTO для полного тикета с сообщениями
 */
@Serializable
data class TicketWithMessagesDto(
    val id: String,
    val userId: String,
    val subject: String,
    val description: String,
    val status: String,
    val priority: String,
    val messages: List<TicketMessageDto>,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * DTO для списка тикетов
 */
@Serializable
data class TicketsListResponse(
    val tickets: List<TicketDto>
)

/**
 * DTO для добавления сообщения в тикет
 */
@Serializable
data class AddTicketMessageRequest(
    val ticketId: String,
    val author: String,  // "user" или "support"
    val content: String
)

