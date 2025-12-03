package com.prike.crmmcpserver.model

/**
 * Модель тикета поддержки
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
    val author: String, // "user" или "support"
    val content: String,
    val timestamp: Long
)

