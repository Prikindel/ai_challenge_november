package com.prike.domain.service

import com.prike.config.LocalLLMConfig
import com.prike.data.client.LocalLLMClient
import com.prike.data.client.LocalLLMException
import com.prike.data.dto.OllamaMessage
import org.slf4j.LoggerFactory

/**
 * Результат генерации от LLM
 */
data class LLMResponse(
    val answer: String,
    val tokensUsed: Int = 0
)

/**
 * Исключение при работе с LLM
 */
class LLMException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Сервис для работы с локальной LLM
 * Используется для анализа данных через локальную LLM на VPS
 */
class LLMService(
    private val localLLMConfig: LocalLLMConfig,
    private val promptTemplateService: PromptTemplateService? = null,
    private val defaultTemplateId: String = "analyst"
) {
    private val logger = LoggerFactory.getLogger(LLMService::class.java)
    
    // Локальная LLM клиент
    private val localLLMClient: LocalLLMClient = LocalLLMClient(
        baseUrl = localLLMConfig.baseUrl,
        defaultModel = localLLMConfig.model,
        timeout = localLLMConfig.timeout,
        apiPath = localLLMConfig.apiPath,
        auth = localLLMConfig.auth
    )
    
    init {
        logger.info("LLMService initialized with local LLM:")
        logger.info("  provider: ${localLLMConfig.provider}")
        logger.info("  model: ${localLLMConfig.model}")
        logger.info("  baseUrl: ${localLLMConfig.baseUrl}")
        logger.info("  temperature: ${localLLMConfig.parameters.temperature}")
    }
    
    /**
     * Генерирует ответ через LLM с использованием шаблона промпта
     * 
     * @param userMessage Сообщение пользователя
     * @param context Контекст (опционально)
     * @param templateId Идентификатор шаблона промпта
     * @param parameters Параметры LLM (если не указаны, используются из конфигурации)
     * @return ответ от LLM и количество использованных токенов
     */
    suspend fun generateResponse(
        userMessage: String,
        context: String? = null,
        templateId: String? = null,
        parameters: com.prike.domain.model.LLMParameters? = null
    ): LLMResponse {
        if (userMessage.isBlank()) {
            throw IllegalArgumentException("User message cannot be blank")
        }
        
        val templateIdToUse = templateId ?: defaultTemplateId
        
        // Применяем шаблон промпта, если доступен PromptTemplateService
        val prompt = if (promptTemplateService != null) {
            promptTemplateService.applyTemplate(templateIdToUse, userMessage, context)
        } else {
            // Если шаблоны не доступны, используем простое сообщение
            if (context != null) {
                "$context\n\nВопрос: $userMessage\n\nОтвет:"
            } else {
                userMessage
            }
        }
        
        // Получаем системный промпт из шаблона, если есть
        val systemPrompt = promptTemplateService?.getSystemPrompt(templateIdToUse)
        
        // Используем переданные параметры или параметры из конфигурации
        val finalParameters = parameters ?: localLLMConfig.parameters
        
        logger.debug("Generating response with template '$templateIdToUse', parameters: temp=${finalParameters.temperature}, maxTokens=${finalParameters.maxTokens}")
        logger.debug("Prompt length: ${prompt.length}")
        
        return try {
            // Формируем messages с правильными ролями
            val messages = mutableListOf<OllamaMessage>()
            
            // Добавляем system prompt отдельно, если есть
            systemPrompt?.let {
                messages.add(OllamaMessage(
                    role = "system",
                    content = it
                ))
            }
            
            // Добавляем user message (промпт из шаблона)
            messages.add(OllamaMessage(
                role = "user",
                content = prompt
            ))
            
            logger.debug("Using local LLM for generation with ${messages.size} messages")
            val answer = localLLMClient.generateWithMessages(
                messages = messages,
                parameters = finalParameters
            )
            
            // Для локальной LLM токены не всегда доступны, используем приблизительную оценку
            val tokensUsed = estimateTokens(userMessage + answer)
            
            LLMResponse(
                answer = answer,
                tokensUsed = tokensUsed
            )
        } catch (e: LocalLLMException) {
            logger.error("Local LLM failed: ${e.message}", e)
            throw LLMException("Не удалось получить ответ от локальной LLM: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Failed to generate answer with local LLM: ${e.message}", e)
            throw LLMException("Ошибка при генерации ответа: ${e.message}", e)
        }
    }
    
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
        temperature: Double? = null
    ): LLMResponse {
        if (question.isBlank()) {
            throw IllegalArgumentException("Question cannot be blank")
        }
        
        logger.debug("Generating answer for question: ${question.take(100)}...")
        
        return try {
            // Формируем messages
            val messages = mutableListOf<OllamaMessage>()
            
            // Добавляем system prompt, если есть
            systemPrompt?.let {
                messages.add(OllamaMessage(
                    role = "system",
                    content = it
                ))
            }
            
            // Добавляем user message
            messages.add(OllamaMessage(
                role = "user",
                content = question
            ))
            
            // Используем переданную temperature или из конфигурации
            val parameters = if (temperature != null) {
                localLLMConfig.parameters.copy(temperature = temperature)
            } else {
                localLLMConfig.parameters
            }
            
            logger.debug("Using local LLM for generation")
            val answer = localLLMClient.generateWithMessages(
                messages = messages,
                parameters = parameters
            )
            
            // Для локальной LLM токены не всегда доступны, используем приблизительную оценку
            val tokensUsed = estimateTokens(question + answer)
            
            LLMResponse(
                answer = answer,
                tokensUsed = tokensUsed
            )
        } catch (e: LocalLLMException) {
            logger.error("Local LLM failed: ${e.message}", e)
            throw LLMException("Не удалось получить ответ от локальной LLM: ${e.message}", e)
        } catch (e: Exception) {
            logger.error("Failed to generate answer with local LLM: ${e.message}", e)
            throw LLMException("Ошибка при генерации ответа: ${e.message}", e)
        }
    }
    
    /**
     * Оценивает количество токенов в тексте (приблизительно)
     * Используется когда точное количество токенов недоступно
     */
    private fun estimateTokens(text: String): Int {
        // Приблизительная оценка: 1 токен ≈ 4 символа для английского текста
        // Для русского текста может быть больше, используем коэффициент 3
        return (text.length / 3).coerceAtLeast(1)
    }
}
