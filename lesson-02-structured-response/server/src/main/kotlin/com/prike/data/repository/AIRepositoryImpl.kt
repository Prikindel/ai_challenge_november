package com.prike.data.repository

import com.prike.data.client.OpenAIClient
import com.prike.domain.exception.AIServiceException
import com.prike.domain.repository.AIRepository

/**
 * Реализация репозитория AI
 */
class AIRepositoryImpl(
    private val aiClient: OpenAIClient
) : AIRepository {
    
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
}
