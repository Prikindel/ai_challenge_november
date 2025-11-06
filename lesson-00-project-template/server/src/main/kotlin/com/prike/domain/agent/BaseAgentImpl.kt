package com.prike.domain.agent

import com.prike.data.client.OpenAIClient
import com.prike.domain.exception.AIServiceException

/**
 * Базовая реализация агента для работы с LLM
 * Содержит минимальную логику запросов к LLM через OpenAIClient
 * 
 * Может служить базой для специализированных агентов
 */
class BaseAgentImpl(
    private val openAIClient: OpenAIClient
) : BaseAgent {
    
    override suspend fun processMessage(userMessage: String): String {
        return try {
            val response = openAIClient.getCompletion(userMessage)
            val content = response.choices.firstOrNull()?.message?.content
                ?: throw AIServiceException("Пустой ответ от AI API (choices пусты)")
            content.trim()
        } catch (e: AIServiceException) {
            throw e
        } catch (e: Exception) {
            throw AIServiceException("Ошибка при получении ответа от AI: ${e.message}", e)
        }
    }
}

