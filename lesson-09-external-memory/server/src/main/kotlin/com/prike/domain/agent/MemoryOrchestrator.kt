package com.prike.domain.agent

import com.prike.domain.service.MemoryService
import com.prike.domain.exception.DomainException
import org.slf4j.LoggerFactory

/**
 * Оркестратор для координации работы ConversationAgent и MemoryService
 * 
 * Отвечает ТОЛЬКО за координацию:
 * - Инициализацию с загрузкой истории
 * - Обработку нового сообщения (координация шагов)
 * - Сброс памяти
 * 
 * НЕ содержит:
 * - Бизнес-логику работы с хранилищем (это в MemoryService и репозитории)
 * - Логику работы с LLM (это в ConversationAgent)
 * - Детали форматирования данных (это в MemoryService)
 */
class MemoryOrchestrator(
    private val conversationAgent: ConversationAgent,
    private val memoryService: MemoryService
) {
    private val logger = LoggerFactory.getLogger(MemoryOrchestrator::class.java)
    
    /**
     * Инициализация оркестратора
     * Загружает историю из памяти при старте приложения
     */
    suspend fun initialize() {
        logger.info("Инициализация MemoryOrchestrator...")
        memoryService.loadHistory().fold(
            onSuccess = {
                val history = memoryService.getHistory()
                logger.info("Оркестратор инициализирован. Загружено ${history.size} сообщений из истории")
            },
            onFailure = { error ->
                logger.warn("Не удалось загрузить историю при инициализации: ${error.message}")
                // Продолжаем работу даже если история не загрузилась
            }
        )
    }
    
    /**
     * Обработка нового сообщения от пользователя
     * 
     * Алгоритм:
     * 1. Загрузить историю из памяти
     * 2. Создать запись для сообщения пользователя
     * 3. Преобразовать историю в формат для LLM
     * 4. Отправить запрос в ConversationAgent
     * 5. Создать запись для ответа ассистента
     * 6. Сохранить оба сообщения в память
     * 
     * @param userMessage сообщение пользователя
     * @return ответ агента с текстом и метаданными
     */
    suspend fun handleMessage(userMessage: String): ConversationAgent.AgentResponse {
        return try {
            // 1. Загрузить историю из памяти
            val history = memoryService.getHistory()
            
            // 2. Создать запись для сообщения пользователя
            val userEntry = memoryService.createUserEntry(userMessage)
            
            // 3. Преобразовать историю в формат для LLM и добавить новое сообщение
            val historyMessages = memoryService.toMessageDtos(history)
            val allMessages = historyMessages + memoryService.toMessageDto(userEntry)
            
            // 4. Отправить запрос в ConversationAgent
            val response = conversationAgent.respond(allMessages)
            
            // 5. Создать запись для ответа ассистента
            val assistantEntry = memoryService.createAssistantEntry(
                content = response.message,
                usage = response.usage
            )
            
            // 6. Сохранить оба сообщения в память
            memoryService.saveEntries(listOf(userEntry, assistantEntry)).fold(
                onSuccess = {
                    logger.debug("Сообщения сохранены в память")
                },
                onFailure = { error ->
                    logger.error("Не удалось сохранить сообщения в память: ${error.message}", error)
                    // Продолжаем работу даже если сохранение не удалось
                }
            )
            
            response
        } catch (e: DomainException) {
            throw e
        } catch (e: Exception) {
            throw DomainException("Ошибка при обработке сообщения: ${e.message}", e)
        }
    }
    
    /**
     * Получить текущую историю диалога
     * @return список записей из памяти
     */
    fun getHistory() = memoryService.getHistory()
    
    /**
     * Сбросить память (очистить всю историю)
     */
    suspend fun reset() {
        logger.info("Сброс памяти...")
        memoryService.clear().fold(
            onSuccess = {
                logger.info("Память успешно сброшена")
            },
            onFailure = { error ->
                logger.error("Ошибка при сбросе памяти: ${error.message}", error)
                throw error
            }
        )
    }
    
    /**
     * Получить статистику памяти
     */
    suspend fun getStats() = memoryService.getStats()
}

