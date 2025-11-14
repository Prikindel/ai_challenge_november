package com.prike.domain.agent

import com.prike.data.dto.MessageDto
import com.prike.data.dto.UsageDto
import com.prike.data.repository.AIRepository
import com.prike.domain.exception.AIServiceException

/**
 * Агент для работы с диалогом через LLM
 * 
 * Отвечает за:
 * - Отправку сообщений в LLM API
 * - Получение ответов от LLM
 * - Обработку ошибок API
 * 
 * НЕ содержит логику работы с памятью (это в MemoryService)
 * НЕ содержит логику координации (это в MemoryOrchestrator)
 */
class ConversationAgent(
    private val aiRepository: AIRepository
) {
    /**
     * Результат ответа агента
     */
    data class AgentResponse(
        val message: String,
        val usage: UsageDto?
    )
    
    /**
     * Получить ответ от LLM на основе истории сообщений
     * @param messages список сообщений (включая system prompt и историю диалога)
     * @return ответ агента с текстом и информацией об использовании токенов
     */
    suspend fun respond(messages: List<MessageDto>): AgentResponse {
        return try {
            val result = aiRepository.getMessageWithHistory(messages)
            AgentResponse(
                message = result.message,
                usage = result.usage
            )
        } catch (e: AIServiceException) {
            throw e
        } catch (e: Exception) {
            throw AIServiceException("Ошибка при получении ответа от агента: ${e.message}", e)
        }
    }
}

