package com.prike.domain.model

/**
 * Запрос на поддержку
 */
data class SupportRequest(
    val ticketId: String?,
    val userId: String?,
    val question: String,
    val context: SupportContext? = null
)

/**
 * Контекст поддержки (информация о пользователе, тикете, RAG-контекст)
 */
data class SupportContext(
    val user: User? = null,
    val ticket: Ticket? = null,
    val userTickets: List<Ticket>? = null,
    val ragContext: String? = null  // контекст из RAG
)

/**
 * Ответ поддержки
 */
data class SupportResponse(
    val answer: String,
    val sources: List<Source>,  // источники из RAG
    val suggestions: List<String>? = null,  // дополнительные предложения
    val ticketId: String? = null,  // если создан новый тикет
    val shouldCreateTicket: Boolean = false  // нужно ли создавать тикет
)

/**
 * Источник информации (из RAG)
 */
data class Source(
    val title: String,
    val content: String,
    val url: String? = null
)

/**
 * Модель пользователя из CRM
 */
data class User(
    val id: String,
    val email: String,
    val name: String?,
    val status: UserStatus,
    val subscription: Subscription?,
    val createdAt: Long
)

/**
 * Статус пользователя
 */
enum class UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED
}

/**
 * Подписка пользователя
 */
data class Subscription(
    val plan: String,
    val expiresAt: Long?
)

/**
 * Модель тикета из CRM
 */
data class Ticket(
    val id: String,
    val userId: String,
    val subject: String,
    val description: String,
    val status: TicketStatus,
    val priority: Priority,
    val messages: List<TicketMessage>,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Статус тикета
 */
enum class TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}

/**
 * Приоритет тикета
 */
enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}

/**
 * Сообщение в тикете
 */
data class TicketMessage(
    val id: String,
    val ticketId: String,
    val author: String,  // "user" или "support"
    val content: String,
    val timestamp: Long
)

