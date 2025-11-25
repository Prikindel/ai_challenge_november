package com.prike.domain.service

import com.prike.config.AIConfig
import com.prike.data.client.OpenAIClient
import com.prike.data.dto.MessageDto
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.SimpleLLMAgent
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с LLM через OpenRouter
 * Использует SimpleLLMAgent (BaseAgent) для соответствия архитектуре других уроков
 */
class LLMService(
    aiConfig: AIConfig,
    private val defaultTemperature: Double = 0.7,
    private val defaultMaxTokens: Int = 2000
) {
    private val logger = LoggerFactory.getLogger(LLMService::class.java)
    
    private val openAIClient = OpenAIClient(
        apiKey = aiConfig.apiKey,
        model = aiConfig.model,
        temperature = defaultTemperature,
        maxTokens = defaultMaxTokens
    )
    
    private val aiRepository = AIRepository(openAIClient)
    private val llmAgent = SimpleLLMAgent(aiRepository)
    
    /**
     * Генерирует ответ на вопрос через LLM
     * 
     * @param question вопрос пользователя
     * @param systemPrompt системный промпт (опционально)
     * @param temperature температура генерации (опционально, не используется в BaseAgent)
     * @return ответ от LLM и количество использованных токенов
     */
    suspend fun generateAnswer(
        question: String,
        systemPrompt: String? = null,
        temperature: Double = defaultTemperature
    ): LLMResponse {
        if (question.isBlank()) {
            throw IllegalArgumentException("Question cannot be blank")
        }
        
        logger.debug("Generating answer for question: ${question.take(100)}...")
        
        return try {
            // Используем BaseAgent для генерации ответа с информацией о токенах
            val agentResponse = llmAgent.generateAnswerWithTokens(question, systemPrompt)
            
            LLMResponse(
                answer = agentResponse.answer,
                tokensUsed = agentResponse.tokensUsed
            )
        } catch (e: Exception) {
            logger.error("Failed to generate answer: ${e.message}", e)
            throw LLMException("Не удалось получить ответ от LLM: ${e.message}", e)
        }
    }
    
    /**
     * Закрывает клиент и освобождает ресурсы
     */
    fun close() {
        openAIClient.close()
    }
}

/**
 * Ответ от LLM
 */
data class LLMResponse(
    val answer: String,
    val tokensUsed: Int
)

/**
 * Исключение при работе с LLM
 */
class LLMException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

