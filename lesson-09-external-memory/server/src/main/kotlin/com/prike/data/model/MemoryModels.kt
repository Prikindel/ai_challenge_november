package com.prike.data.model

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Роль сообщения в диалоге
 */
@Serializable
enum class MessageRole {
    USER,      // Сообщение от пользователя
    ASSISTANT, // Ответ ассистента
    SUMMARY    // Суммаризация диалога
}

/**
 * Метаданные записи в памяти
 * Содержит информацию о токенах, модели и других метриках
 */
@Serializable
data class MemoryMetadata(
    val model: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)

/**
 * Запись в памяти (сообщение пользователя или ответ ассистента)
 * 
 * @param id уникальный идентификатор записи
 * @param role роль сообщения (USER или ASSISTANT)
 * @param content содержимое сообщения
 * @param timestamp временная метка создания записи
 * @param metadata метаданные (токены, модель и т.д.)
 */
@Serializable
data class MemoryEntry(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long, // Instant как Long (миллисекунды с эпохи)
    val metadata: MemoryMetadata? = null
) {
    /**
     * Получить Instant из timestamp
     */
    fun getTimestampInstant(): Instant = Instant.ofEpochMilli(timestamp)
    
    companion object {
        /**
         * Создать MemoryEntry с текущим временем
         */
        fun create(
            id: String,
            role: MessageRole,
            content: String,
            metadata: MemoryMetadata? = null
        ): MemoryEntry {
            return MemoryEntry(
                id = id,
                role = role,
                content = content,
                timestamp = Instant.now().toEpochMilli(),
                metadata = metadata
            )
        }
    }
}

/**
 * Статистика памяти
 */
data class MemoryStats(
    val totalEntries: Int,
    val userMessages: Int,
    val assistantMessages: Int,
    val oldestEntry: Instant?,
    val newestEntry: Instant?
)

