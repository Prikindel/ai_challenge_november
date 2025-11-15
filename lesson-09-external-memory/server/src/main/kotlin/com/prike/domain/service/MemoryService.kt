package com.prike.domain.service

import com.prike.data.model.MemoryEntry
import com.prike.data.model.MemoryMetadata
import com.prike.data.model.MemoryStats
import com.prike.data.model.MessageRole
import com.prike.data.dto.UsageDto
import com.prike.domain.exception.MemoryException
import com.prike.domain.repository.MemoryRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Сервис для управления памятью диалога
 * 
 * Отвечает за:
 * - Создание записей памяти из сообщений
 * - Сохранение и загрузку истории
 * - Преобразование между форматами (MemoryEntry <-> MessageDto)
 * - Управление жизненным циклом памяти
 * 
 * НЕ содержит логику работы с хранилищем (это в репозитории)
 * НЕ содержит логику работы с LLM (это в ConversationAgent)
 */
class MemoryService(
    private val repository: MemoryRepository
) {
    private val logger = LoggerFactory.getLogger(MemoryService::class.java)
    
    /**
     * Текущая история диалога в памяти
     */
    private var cachedHistory: List<MemoryEntry> = emptyList()
    
    /**
     * Загрузить историю из хранилища
     * Вызывается при инициализации агента
     */
    suspend fun loadHistory(): Result<Unit> {
        return repository.loadAll().fold(
            onSuccess = { entries ->
                cachedHistory = entries.sortedBy { it.timestamp }
                logger.info("Загружено ${entries.size} записей из памяти")
                Result.success(Unit)
            },
            onFailure = { error ->
                logger.error("Ошибка загрузки истории: ${error.message}", error)
                Result.failure(MemoryException("Не удалось загрузить историю: ${error.message}", error))
            }
        )
    }
    
    /**
     * Получить текущую историю диалога
     * @return список записей, отсортированных по времени (старые первыми)
     */
    fun getHistory(): List<MemoryEntry> {
        return cachedHistory.toList()
    }
    
    /**
     * Создать запись для сообщения пользователя
     * @param content содержимое сообщения
     * @return новая запись MemoryEntry
     */
    fun createUserEntry(content: String): MemoryEntry {
        return MemoryEntry.create(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = content
        )
    }
    
    /**
     * Создать запись для ответа ассистента
     * @param content содержимое ответа
     * @param usage информация об использовании токенов (опционально)
     * @return новая запись MemoryEntry
     */
    fun createAssistantEntry(
        content: String,
        usage: UsageDto? = null
    ): MemoryEntry {
        val metadata = usage?.let {
            MemoryMetadata(
                promptTokens = it.promptTokens,
                completionTokens = it.completionTokens,
                totalTokens = it.totalTokens
            )
        }
        
        return MemoryEntry.create(
            id = UUID.randomUUID().toString(),
            role = MessageRole.ASSISTANT,
            content = content,
            metadata = metadata
        )
    }
    
    /**
     * Сохранить одну или несколько записей в память
     * @param entries записи для сохранения
     * @return Result с Unit при успехе или ошибкой при неудаче
     */
    suspend fun saveEntries(entries: List<MemoryEntry>): Result<Unit> {
        if (entries.isEmpty()) {
            return Result.success(Unit)
        }
        
        return repository.saveAll(entries).fold(
            onSuccess = {
                // Обновить кэш истории
                cachedHistory = cachedHistory + entries
                logger.debug("Сохранено ${entries.size} записей в память")
                Result.success(Unit)
            },
            onFailure = { error ->
                logger.error("Ошибка сохранения записей: ${error.message}", error)
                Result.failure(MemoryException("Не удалось сохранить записи: ${error.message}", error))
            }
        )
    }
    
    /**
     * Преобразовать MemoryEntry в MessageDto для отправки в LLM
     * @param entry запись из памяти
     * @return MessageDto для API
     */
    fun toMessageDto(entry: MemoryEntry): com.prike.data.dto.MessageDto {
        val role = when (entry.role) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
        }
        return com.prike.data.dto.MessageDto(
            role = role,
            content = entry.content
        )
    }
    
    /**
     * Преобразовать список MemoryEntry в список MessageDto
     * @param entries записи из памяти
     * @return список MessageDto для API
     */
    fun toMessageDtos(entries: List<MemoryEntry>): List<com.prike.data.dto.MessageDto> {
        return entries.map { toMessageDto(it) }
    }
    
    /**
     * Очистить всю память
     * @return Result с Unit при успехе или ошибкой при неудаче
     */
    suspend fun clear(): Result<Unit> {
        return repository.clear().fold(
            onSuccess = {
                cachedHistory = emptyList()
                logger.info("Память очищена")
                Result.success(Unit)
            },
            onFailure = { error ->
                logger.error("Ошибка очистки памяти: ${error.message}", error)
                Result.failure(MemoryException("Не удалось очистить память: ${error.message}", error))
            }
        )
    }
    
    /**
     * Получить статистику памяти
     * @return Result со статистикой
     */
    suspend fun getStats(): Result<MemoryStats> {
        return repository.getStats()
    }
}

