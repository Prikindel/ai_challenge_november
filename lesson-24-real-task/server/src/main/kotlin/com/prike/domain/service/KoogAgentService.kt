package com.prike.domain.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import com.prike.config.KoogConfig
import com.prike.domain.tools.ReviewsTools
import org.slf4j.LoggerFactory

/**
 * Сервис для создания и настройки Koog AIAgent с инструментами
 */
class KoogAgentService(
    private val koogConfig: KoogConfig,
    private val reviewsTools: ReviewsTools
) {
    private val logger = LoggerFactory.getLogger(KoogAgentService::class.java)

    /**
     * Создает и настраивает Koog AIAgent для анализа отзывов с инструментами
     */
    fun createAgent(): AIAgent<String, String> {
        if (!koogConfig.enabled) {
            throw IllegalStateException("Koog is disabled in configuration")
        }

        logger.info("Initializing Koog AIAgent with model: ${koogConfig.model}")

        val systemPrompt = """
            Ты - эксперт по анализу отзывов пользователей мобильных приложений.
            
            Твоя задача:
            1. Классифицировать отзывы на POSITIVE, NEGATIVE или NEUTRAL
            2. Определять темы и категории проблем/улучшений
            3. Оценивать критичность проблем (HIGH, MEDIUM, LOW)
            4. Сравнивать статистику между неделями
            5. Генерировать структурированные отчеты
            
            Всегда отвечай в формате JSON, строго следуя структуре данных.
            Будь точным и объективным в анализе.
        """.trimIndent()

        val executor = simpleOpenAIExecutor(koogConfig.apiKey)
        
        val model = when (koogConfig.model.lowercase()) {
            "gpt-4o-mini", "gpt4o-mini" -> OpenAIModels.Chat.GPT4o
            "gpt-4o", "gpt4o" -> OpenAIModels.Chat.GPT4o
            "gpt-4-turbo", "gpt4-turbo" -> OpenAIModels.Chat.GPT4o
            else -> {
                logger.warn("Unknown model ${koogConfig.model}, using GPT4o")
                OpenAIModels.Chat.GPT4o
            }
        }

        // TODO: Регистрация инструментов из ReviewsTools
        // Пока создаем агента без инструментов, инструменты будут добавлены позже
        
        logger.info("Creating AIAgent")

        return AIAgent(
            promptExecutor = executor,
            llmModel = model,
            systemPrompt = systemPrompt
        )
    }
}

