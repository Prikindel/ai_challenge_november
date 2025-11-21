package com.prike.data.repository

import com.prike.data.client.OpenAIClient
import com.prike.data.dto.MessageDto
import com.prike.data.dto.OpenAIResponse
import com.prike.data.dto.ToolDto
import com.prike.domain.exception.AIServiceException

/**
 * Репозиторий для работы с LLM API
 * Выполняет запросы к LLM и возвращает готовые данные
 */
class AIRepository(
    private val openAIClient: OpenAIClient
) {
    /**
     * Получить ответ от LLM с использованием истории сообщений и tools
     * @param messages список сообщений (включая system prompt и историю диалога)
     * @param tools список доступных инструментов (опционально)
     * @return результат с ответом и JSON запросом/ответом
     */
    suspend fun getMessageWithTools(
        messages: List<MessageDto>,
        tools: List<ToolDto>? = null
    ): OpenAIClient.CompletionResult {
        return try {
            openAIClient.getCompletionWithTools(messages, tools)
        } catch (e: AIServiceException) {
            throw e
        } catch (e: Exception) {
            throw AIServiceException("Ошибка при получении ответа от AI: ${e.message}", e)
        }
    }
    
    /**
     * Извлечь текстовый контент из ответа LLM
     */
    fun extractContent(response: OpenAIResponse): String {
        return response.choices.firstOrNull()?.message?.content?.trim()
            ?: throw AIServiceException("Пустой ответ от AI API (choices пусты)")
    }
}

