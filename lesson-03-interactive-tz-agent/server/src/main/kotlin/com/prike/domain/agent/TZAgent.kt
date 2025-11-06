package com.prike.domain.agent

import com.prike.data.dto.TechnicalSpec
import com.prike.data.mapper.TZMapper
import com.prike.data.repository.AIRepository
import com.prike.domain.exception.AIServiceException

/**
 * Специализированный агент для сбора требований и создания ТЗ
 * 
 * Наследуется от BaseAgent для базовой логики работы с репозиторием
 * Использует TZMapper для преобразования JSON ответов в объекты ТЗ
 * 
 * Особенности:
 * - Управляет историей сообщений для контекста разговора
 * - LLM всегда возвращает JSON в формате TZResponse (status и content)
 * - Делегирует парсинг ответа мапперу
 */
class TZAgent(
    aiRepository: AIRepository,
    private val tzMapper: TZMapper,
    private val systemPrompt: String
) : BaseAgent(aiRepository) {
    private val messageHistory = MessageHistory(systemPrompt = systemPrompt)
    
    /**
     * История JSON запросов и ответов для отладки
     * Каждая запись содержит JSON запрос и ответ от LLM
     */
    private val jsonHistory = mutableListOf<JsonHistoryEntry>()
    
    /**
     * Запись в истории JSON
     */
    data class JsonHistoryEntry(
        val requestJson: String,
        val responseJson: String
    )
    
    /**
     * Обработать сообщение пользователя
     * @param userMessage сообщение пользователя
     * @return результат: либо продолжение диалога, либо готовое ТЗ, с JSON запросом и ответом
     */
    suspend fun processMessage(userMessage: String): TZAgentResult {
        // Добавляем сообщение пользователя в историю
        messageHistory.addUserMessage(userMessage)
        
        // Получаем ответ от LLM через базовый класс с использованием истории
        val messageResult = try {
            aiRepository.getMessageWithHistory(messageHistory.getMessages())
        } catch (e: Exception) {
            throw AIServiceException("Ошибка при получении ответа от AI: ${e.message}", e)
        }
        
        // Парсим ответ через маппер (ожидается JSON в формате TZResponse)
        val parsedResult = try {
            tzMapper.parseResponse(messageResult.message)
        } catch (e: Exception) {
            throw AIServiceException("Ошибка при парсинге ответа от AI: ${e.message}", e)
        }
        
        // Сохраняем JSON запрос и ответ в историю
        jsonHistory.add(JsonHistoryEntry(
            requestJson = messageResult.requestJson,
            responseJson = messageResult.responseJson
        ))
        
        // Обрабатываем результат
        return when (parsedResult) {
            is TZMapper.TZResponseResult.Ready -> {
                // ТЗ готово - добавляем ответ в историю и возвращаем результат
                messageHistory.addAssistantMessage(messageResult.message)
                TZAgentResult.TZReady(
                    technicalSpec = parsedResult.technicalSpec,
                    requestJson = messageResult.requestJson,
                    responseJson = messageResult.responseJson
                )
            }
            is TZMapper.TZResponseResult.Continue -> {
                // Продолжаем диалог - добавляем ответ в историю
                messageHistory.addAssistantMessage(messageResult.message)
                TZAgentResult.Continue(
                    message = parsedResult.message,
                    requestJson = messageResult.requestJson,
                    responseJson = messageResult.responseJson
                )
            }
        }
    }
    
    /**
     * Очистить историю сообщений (начать новый диалог)
     */
    fun clearHistory() {
        messageHistory.clear()
        jsonHistory.clear()
    }
    
    /**
     * Получить всю историю JSON запросов и ответов
     */
    fun getJsonHistory(): List<JsonHistoryEntry> {
        return jsonHistory.toList()
    }
    
    /**
     * Результат работы агента
     */
    sealed class TZAgentResult {
        /**
         * ТЗ готово
         */
        data class TZReady(
            val technicalSpec: TechnicalSpec,
            val requestJson: String,
            val responseJson: String
        ) : TZAgentResult()
        
        /**
         * Продолжаем диалог
         */
        data class Continue(
            val message: String,
            val requestJson: String,
            val responseJson: String
        ) : TZAgentResult()
    }
}
