package com.prike.data.repository

import com.prike.data.client.OpenAIClient
import com.prike.data.dto.MessageDto
import com.prike.data.dto.OpenAIResponse
import com.prike.domain.exception.AIServiceException

/**
 * Репозиторий для работы с DTO от LLM API
 * Выполняет запросы к LLM и возвращает готовые данные (текстовые ответы)
 * 
 * Вся логика работы с OpenAIClient находится здесь
 * Агенты получают уже обработанные данные (строки)
 */
class AIRepository(
    private val openAIClient: OpenAIClient
) {
    /**
     * Получить текстовый ответ от LLM по сообщению пользователя
     * @param userMessage сообщение пользователя
     * @return текстовый ответ от LLM
     */
    suspend fun getMessage(userMessage: String): String {
        return try {
            val response = openAIClient.getCompletion(userMessage)
            response.choices.firstOrNull()?.message?.content?.trim()
                ?: throw AIServiceException("Пустой ответ от AI API (choices пусты)")
        } catch (e: AIServiceException) {
            throw e
        } catch (e: Exception) {
            throw AIServiceException("Ошибка при получении ответа от AI: ${e.message}", e)
        }
    }
    
    /**
     * Результат запроса к LLM с текстовым ответом и JSON
     */
    data class MessageResult(
        val message: String,
        val requestJson: String,
        val responseJson: String
    )
    
    /**
     * Получить текстовый ответ от LLM с использованием истории сообщений
     * @param messages список сообщений (включая system prompt и историю диалога)
     * @return результат с текстовым ответом и JSON запросом/ответом
     */
    suspend fun getMessageWithHistory(messages: List<MessageDto>): MessageResult {
        return try {
            val completionResult = openAIClient.getCompletionWithHistory(messages)
            val message = completionResult.response.choices.firstOrNull()?.message?.content?.trim()
                ?: throw AIServiceException("Пустой ответ от AI API (choices пусты)")
            MessageResult(
                message = message,
                requestJson = completionResult.requestJson,
                responseJson = completionResult.responseJson
            )
        } catch (e: AIServiceException) {
            throw e
        } catch (e: Exception) {
            throw AIServiceException("Ошибка при получении ответа от AI: ${e.message}", e)
        }
    }
    
    /**
     * Получить сырой ответ от LLM API (для специальных случаев)
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

