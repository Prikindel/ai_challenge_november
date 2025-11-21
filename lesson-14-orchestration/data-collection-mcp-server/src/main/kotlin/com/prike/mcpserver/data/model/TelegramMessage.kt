package com.prike.mcpserver.data.model

/**
 * Модель сообщения из Telegram для хранения в БД
 */
data class TelegramMessage(
    val id: String,  // UUID для БД
    val messageId: Long,  // ID сообщения в Telegram
    val groupId: String,  // ID группы/чата
    val content: String,  // Текст сообщения
    val author: String? = null,  // Имя автора (если доступно)
    val timestamp: Long,  // Unix timestamp в миллисекундах
    val createdAt: Long  // Время создания записи в БД
)

