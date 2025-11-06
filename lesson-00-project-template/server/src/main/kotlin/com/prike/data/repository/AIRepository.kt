package com.prike.data.repository

import com.prike.data.client.OpenAIClient
import com.prike.data.dto.OpenAIResponse
import com.prike.domain.exception.AIServiceException

/**
 * Репозиторий для работы с DTO от LLM API
 * Выполняет парсинг, валидацию и трансформацию данных от LLM
 * 
 * Используется агентами для работы с данными
 * Не содержит бизнес-логику, только работу с DTO
 */
class AIRepository(
    private val openAIClient: OpenAIClient
) {
    /**
     * Получить сырой ответ от LLM API
     * @param userMessage сообщение пользователя
     * @return сырой ответ от LLM (OpenAIResponse)
     */
    suspend fun getRawResponse(userMessage: String): OpenAIResponse {
        return try {
            openAIClient.getCompletion(userMessage)
        } catch (e: AIServiceException) {
            throw e
        } catch (e: Exception) {
            throw AIServiceException("Ошибка при получении ответа от AI: ${e.message}", e)
        }
    }
    
    /**
     * Извлечь текстовый контент из ответа LLM
     * @param response ответ от LLM API
     * @return текстовый контент
     */
    fun extractContent(response: OpenAIResponse): String {
        return response.choices.firstOrNull()?.message?.content?.trim()
            ?: throw AIServiceException("Пустой ответ от AI API (choices пусты)")
    }
}

