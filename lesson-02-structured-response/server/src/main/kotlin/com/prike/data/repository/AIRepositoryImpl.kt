package com.prike.data.repository

import com.prike.data.client.OpenAIClient
import com.prike.data.dto.ChatStructuredResponse
import com.prike.domain.exception.AIServiceException
import com.prike.domain.repository.AIRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Реализация репозитория AI
 */
class AIRepositoryImpl(
    private val aiClient: OpenAIClient
) : AIRepository {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override suspend fun getAIResponse(userMessage: String): String {
        return try {
            val response = aiClient.getCompletion(userMessage)

            response.choices.firstOrNull()?.message?.content
                ?: throw AIServiceException("Пустой ответ от AI API")
        } catch (e: AIServiceException) {
            throw e
        } catch (e: Exception) {
            throw AIServiceException(
                "Ошибка при получении ответа от AI: ${e.message}",
                e
            )
        }
    }
    
    /**
     * Получить структурированный JSON ответ от AI
     * Парсит JSON из ответа LLM и возвращает структурированный объект
     */
    suspend fun getStructuredAIResponse(userMessage: String): ChatStructuredResponse {
        return try {
            val response = aiClient.getCompletion(userMessage)
            val content = response.choices.firstOrNull()?.message?.content
                ?: throw AIServiceException("Пустой ответ от AI API")
            
            // Парсим JSON из строки ответа
            try {
                json.decodeFromString<ChatStructuredResponse>(content)
            } catch (e: Exception) {
                throw AIServiceException(
                    "Не удалось распарсить JSON ответ от AI: ${e.message}. " +
                    "Ответ: $content",
                    e
                )
            }
        } catch (e: AIServiceException) {
            throw e
        } catch (e: Exception) {
            throw AIServiceException(
                "Ошибка при получении структурированного ответа от AI: ${e.message}",
                e
            )
        }
    }
}
