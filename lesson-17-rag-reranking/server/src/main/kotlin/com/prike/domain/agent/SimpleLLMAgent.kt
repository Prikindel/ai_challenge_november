package com.prike.domain.agent

import com.prike.data.dto.MessageDto
import com.prike.data.repository.AIRepository
import org.slf4j.LoggerFactory

/**
 * Простой LLM агент для генерации ответов
 * Наследуется от BaseAgent для соответствия архитектуре других уроков
 */
class SimpleLLMAgent(
    aiRepository: AIRepository
) : BaseAgent(aiRepository) {
    private val logger = LoggerFactory.getLogger(SimpleLLMAgent::class.java)
    
    /**
     * Генерирует ответ на вопрос с системным промптом
     * Возвращает только текст ответа (использует метод из BaseAgent)
     */
    suspend fun generateAnswer(
        question: String,
        systemPrompt: String? = null
    ): String {
        if (question.isBlank()) {
            throw IllegalArgumentException("Question cannot be blank")
        }
        
        logger.debug("Generating answer for question: ${question.take(100)}...")
        
        // Используем метод из BaseAgent для простых запросов
        val answer = getMessage(question)
        
        logger.debug("Generated answer: ${answer.take(100)}...")
        
        return answer
    }
    
    /**
     * Генерирует ответ на вопрос с системным промптом и возвращает информацию о токенах
     */
    suspend fun generateAnswerWithTokens(
        question: String,
        systemPrompt: String? = null
    ): AgentResponse {
        if (question.isBlank()) {
            throw IllegalArgumentException("Question cannot be blank")
        }
        
        logger.debug("Generating answer for question: ${question.take(100)}...")
        
        val messages = mutableListOf<MessageDto>()
        
        // Добавляем системный промпт, если указан
        if (!systemPrompt.isNullOrBlank()) {
            messages.add(MessageDto(role = "system", content = systemPrompt))
        }
        
        // Добавляем вопрос пользователя
        messages.add(MessageDto(role = "user", content = question))
        
        val response = aiRepository.getMessage(messages)
        val answer = aiRepository.extractContent(response)
        val tokensUsed = response.usage?.totalTokens ?: 0
        
        logger.debug("Generated answer: ${answer.take(100)}... (tokens: $tokensUsed)")
        
        return AgentResponse(
            answer = answer,
            tokensUsed = tokensUsed
        )
    }
    
    /**
     * Ответ агента с информацией о токенах
     */
    data class AgentResponse(
        val answer: String,
        val tokensUsed: Int
    )
}

