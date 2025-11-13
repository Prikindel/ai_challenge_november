package com.prike.domain.agent

import com.prike.data.dto.MessageDto
import com.prike.data.repository.AIRepository

/**
 * Базовый абстрактный класс для агентов, работающих с LLM
 * Использует AIRepository для получения данных от LLM
 * 
 * Вся логика работы с LLM API находится в репозитории
 * Агенты получают уже готовые данные (строки) и работают с ними
 */
abstract class BaseAgent(
    protected val aiRepository: AIRepository
) {
    /**
     * Получить ответ от LLM по сообщению пользователя
     * @param userMessage сообщение пользователя
     * @return текстовый ответ от LLM
     */
    suspend fun getMessage(userMessage: String): String {
        return aiRepository.getMessage(userMessage)
    }
    
    /**
     * Получить ответ от LLM с использованием истории сообщений
     * @param messages список сообщений (включая system prompt и историю диалога)
     * @return результат с текстовым ответом и JSON запросом/ответом
     */
    protected suspend fun getMessageWithHistory(messages: List<MessageDto>): AIRepository.MessageResult {
        return aiRepository.getMessageWithHistory(messages)
    }
}
