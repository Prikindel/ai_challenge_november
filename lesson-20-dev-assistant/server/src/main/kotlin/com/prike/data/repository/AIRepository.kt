package com.prike.data.repository

import com.prike.data.client.OpenAIClient
import com.prike.data.dto.MessageDto
import com.prike.data.dto.OpenAIResponse
import com.prike.domain.service.LLMException

/**
 * Репозиторий для работы с LLM API
 * Выполняет запросы к LLM и возвращает готовые данные
 */
class AIRepository(
    private val openAIClient: OpenAIClient
) {
    /**
     * Получить текстовый ответ от LLM по сообщению пользователя
     */
    suspend fun getMessage(userMessage: String): String {
        return try {
            val messages = listOf(MessageDto(role = "user", content = userMessage))
            val response = openAIClient.chatCompletion(messages)
            response.choices.firstOrNull()?.message?.content?.trim()
                ?: throw LLMException("Пустой ответ от AI API (choices пусты)")
        } catch (e: LLMException) {
            throw e
        } catch (e: Exception) {
            throw LLMException("Ошибка при получении ответа от AI: ${e.message}", e)
        }
    }
    
    /**
     * Получить ответ от LLM с использованием истории сообщений
     * @param messages список сообщений (включая system prompt и историю диалога)
     * @return ответ от LLM
     */
    suspend fun getMessage(messages: List<MessageDto>): OpenAIResponse {
        return try {
            openAIClient.chatCompletion(messages)
        } catch (e: Exception) {
            throw LLMException("Ошибка при получении ответа от AI: ${e.message}", e)
        }
    }
    
    /**
     * Извлечь текстовый контент из ответа LLM
     */
    fun extractContent(response: OpenAIResponse): String {
        return response.choices.firstOrNull()?.message?.content?.trim()
            ?: throw LLMException("Пустой ответ от AI API (choices пусты)")
    }
}

