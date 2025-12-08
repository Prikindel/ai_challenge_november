package com.prike.domain.service

import com.prike.config.AIConfig
import com.prike.config.LocalLLMConfig
import com.prike.data.client.LocalLLMClient
import com.prike.data.client.LocalLLMException
import com.prike.mcpcommon.client.OpenAIClient
import com.prike.mcpcommon.dto.MessageDto
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.SimpleLLMAgent
import org.slf4j.LoggerFactory
import kotlinx.serialization.json.*

/**
 * Сервис для работы с LLM через OpenRouter или локальную LLM
 * Поддерживает переключение между локальной и внешней LLM
 * Использует SimpleLLMAgent (BaseAgent) для соответствия архитектуре других уроков
 */
class LLMService(
    aiConfig: AIConfig,
    localLLMConfig: LocalLLMConfig? = null,
    private val defaultTemperature: Double = 0.7,
    private val defaultMaxTokens: Int = 2000
) {
    private val logger = LoggerFactory.getLogger(LLMService::class.java)
    
    // Сохраняем конфигурацию локальной LLM
    private val localLLMConfigValue: LocalLLMConfig? = localLLMConfig
    
    // Локальная LLM клиент (если включена)
    private val localLLMClient: LocalLLMClient? = localLLMConfigValue?.let {
        if (it.enabled) {
            logger.info("Local LLM enabled: provider=${it.provider}, model=${it.model}, baseUrl=${it.baseUrl}")
            LocalLLMClient(
                baseUrl = it.baseUrl,
                defaultModel = it.model,
                timeout = it.timeout
            )
        } else {
            null
        }
    }
    
    // Внешняя LLM клиент (OpenRouter)
    private val openAIClient = OpenAIClient(
        apiKey = aiConfig.apiKey,
        model = aiConfig.model,
        temperature = defaultTemperature,
        maxTokens = defaultMaxTokens
    )
    
    private val aiRepository = AIRepository(openAIClient)
    private val llmAgent = SimpleLLMAgent(aiRepository)
    
    // Флаг использования локальной LLM
    private val useLocalLLM: Boolean = localLLMConfigValue?.enabled == true && localLLMClient != null
    
    /**
     * Генерирует ответ на вопрос через LLM
     * 
     * @param question вопрос пользователя
     * @param systemPrompt системный промпт (опционально)
     * @param temperature температура генерации
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
        
        logger.debug("Generating answer for question: ${question.take(100)}... (useLocalLLM: $useLocalLLM)")
        
        // Если локальная LLM включена, используем её
        if (useLocalLLM && localLLMClient != null) {
            return try {
                // Формируем промпт с системным сообщением, если есть
                val prompt = if (systemPrompt != null) {
                    buildString {
                        append("System: $systemPrompt\n\n")
                        append("User: $question\n\n")
                        append("Assistant: ")
                    }
                } else {
                    buildString {
                        append("User: $question\n\n")
                        append("Assistant: ")
                    }
                }
                
                logger.debug("Using local LLM for generation")
                val answer = localLLMClient.generate(
                    prompt = prompt,
                    temperature = temperature
                )
                
                // Для локальной LLM токены не всегда доступны, используем приблизительную оценку
                val tokensUsed = estimateTokens(question + answer)
                
                LLMResponse(
                    answer = answer,
                    tokensUsed = tokensUsed
                )
            } catch (e: LocalLLMException) {
                logger.warn("Local LLM failed: ${e.message}, falling back to OpenRouter")
                // Fallback на внешнюю LLM при ошибке
                return generateAnswerExternal(question, systemPrompt, temperature)
            } catch (e: Exception) {
                logger.error("Failed to generate answer with local LLM: ${e.message}", e)
                // Fallback на внешнюю LLM при ошибке
                return generateAnswerExternal(question, systemPrompt, temperature)
            }
        }
        
        // Используем внешнюю LLM (OpenRouter)
        return generateAnswerExternal(question, systemPrompt, temperature)
    }
    
    /**
     * Генерирует ответ через внешнюю LLM (OpenRouter)
     */
    private suspend fun generateAnswerExternal(
        question: String,
        systemPrompt: String?,
        temperature: Double
    ): LLMResponse {
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
        
        logger.debug("Generating answer with ${messages.size} messages (useLocalLLM: $useLocalLLM)")
        
        // Если локальная LLM включена, используем её
        if (useLocalLLM && localLLMClient != null) {
            return try {
                // Преобразуем messages в prompt для локальной LLM
                val prompt = buildPromptFromMessages(messages)
                
                logger.debug("Using local LLM for generation")
                val answer = localLLMClient.generate(
                    prompt = prompt,
                    temperature = temperature
                )
                
                // Для локальной LLM токены не всегда доступны, используем приблизительную оценку
                val tokensUsed = estimateTokens(answer)
                
                LLMResponse(
                    answer = answer,
                    tokensUsed = tokensUsed
                )
            } catch (e: LocalLLMException) {
                logger.warn("Local LLM failed: ${e.message}, falling back to OpenRouter")
                // Fallback на внешнюю LLM при ошибке
                return generateAnswerWithMessagesExternal(messages, temperature)
            } catch (e: Exception) {
                logger.error("Failed to generate answer with local LLM: ${e.message}", e)
                // Fallback на внешнюю LLM при ошибке
                return generateAnswerWithMessagesExternal(messages, temperature)
            }
        }
        
        // Используем внешнюю LLM (OpenRouter)
        return generateAnswerWithMessagesExternal(messages, temperature)
    }
    
    /**
     * Генерирует ответ через внешнюю LLM (OpenRouter)
     */
    private suspend fun generateAnswerWithMessagesExternal(
        messages: List<MessageDto>,
        temperature: Double
    ): LLMResponse {
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
     * Преобразует массив messages в prompt для локальной LLM
     * Локальные LLM обычно работают с простым текстовым промптом
     * Формат оптимизирован для llama3.2 и других моделей
     */
    private fun buildPromptFromMessages(messages: List<MessageDto>): String {
        return buildString {
            var hasSystem = false
            var hasUser = false
            
            messages.forEach { message ->
                when (message.role) {
                    "system" -> {
                        if (!hasSystem) {
                            append("System: ${message.content}\n\n")
                            hasSystem = true
                        }
                    }
                    "user" -> {
                        if (hasUser) {
                            append("\n")
                        }
                        append("User: ${message.content}")
                        hasUser = true
                    }
                    "assistant" -> {
                        if (hasUser) {
                            append("\n")
                        }
                        append("Assistant: ${message.content}")
                        hasUser = false
                    }
                }
            }
            
            // Если последнее сообщение было от пользователя, добавляем начало ответа ассистента
            if (hasUser) {
                append("\nAssistant: ")
            } else {
                append("\n\nUser: ")
            }
        }
    }
    
    /**
     * Приблизительная оценка количества токенов (для локальной LLM)
     * Используется простая эвристика: ~4 символа на токен
     */
    private fun estimateTokens(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
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
     * Проверяет доступность локальной LLM
     */
    suspend fun checkLocalLLMAvailability(): Boolean {
        return localLLMClient?.checkAvailability() ?: false
    }
    
    /**
     * Получает информацию о текущем провайдере LLM
     */
    fun getProviderInfo(): String {
        return if (useLocalLLM && localLLMConfigValue != null) {
            "local (${localLLMConfigValue.provider}: ${localLLMConfigValue.model})"
        } else {
            "openrouter (${openAIClient.model})"
        }
    }
    
    /**
     * Получает конфигурацию локальной LLM (если включена)
     */
    fun getLocalLLMConfig(): LocalLLMConfig? = localLLMConfigValue
    
    /**
     * Закрывает клиенты и освобождает ресурсы
     */
    fun close() {
        localLLMClient?.close()
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


