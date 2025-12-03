package com.prike.domain.service

import com.prike.config.AIConfig
import com.prike.mcpcommon.client.OpenAIClient
import com.prike.mcpcommon.dto.MessageDto
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.SimpleLLMAgent
import org.slf4j.LoggerFactory
import kotlinx.serialization.json.*

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
     * Генерирует ответ через LLM с использованием массива messages (для чата с историей)
     * 
     * @param messages массив сообщений (system, user, assistant, user, ...)
     * @param temperature температура генерации
     * @return ответ от LLM и количество использованных токенов
     */
    suspend fun generateAnswerWithMessages(
        messages: List<MessageDto>,
        temperature: Double = defaultTemperature
    ): LLMResponse {
        if (messages.isEmpty()) {
            throw IllegalArgumentException("Messages cannot be empty")
        }
        
        logger.debug("Generating answer with ${messages.size} messages")
        
        return try {
            val response = openAIClient.chatCompletion(messages, temperature)
            val answer = response.choices.firstOrNull()?.message?.content ?: ""
            val tokensUsed = response.usage?.totalTokens ?: 0
            
            LLMResponse(
                answer = answer,
                tokensUsed = tokensUsed
            )
        } catch (e: Exception) {
            logger.error("Failed to generate answer: ${e.message}", e)
            throw LLMException("Не удалось получить ответ от LLM: ${e.message}", e)
        }
    }
    
    /**
     * Генерирует структурированный JSON ответ через LLM (использует JSON mode)
     * 
     * @param messages массив сообщений
     * @param temperature температура генерации
     * @return ответ от LLM в виде JSON строки и количество использованных токенов
     */
    suspend fun generateStructuredJsonAnswer(
        messages: List<MessageDto>,
        temperature: Double = defaultTemperature
    ): LLMResponse {
        if (messages.isEmpty()) {
            throw IllegalArgumentException("Messages cannot be empty")
        }
        
        logger.debug("Generating structured JSON answer with ${messages.size} messages")
        
        return try {
            // Используем JSON mode для структурированного ответа
            val responseFormat = kotlinx.serialization.json.buildJsonObject {
                put("type", "json_object")
            }
            
            val response = openAIClient.chatCompletion(
                messages = messages,
                temperature = temperature,
                responseFormat = responseFormat
            )
            val answer = response.choices.firstOrNull()?.message?.content ?: ""
            val tokensUsed = response.usage?.totalTokens ?: 0
            
            LLMResponse(
                answer = answer,
                tokensUsed = tokensUsed
            )
        } catch (e: Exception) {
            logger.error("Failed to generate structured JSON answer: ${e.message}", e)
            throw LLMException("Не удалось получить структурированный ответ от LLM: ${e.message}", e)
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

