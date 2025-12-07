package com.prike.domain.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
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
            Ты - умный помощник для анализа отзывов пользователей мобильных приложений.
            
            Твоя задача - помогать пользователю анализировать отзывы, сравнивать статистику и генерировать отчеты.
            
            У тебя есть доступ к инструментам для:
            - Получения отзывов из API за указанный период
            - Сохранения саммари отзывов в базу данных
            - Получения саммари отзывов за неделю или всех саммари
            - Получения статистики по неделям
            - Отправки сообщений в Telegram (если настроено)
            
            Когда пользователь просит выполнить задачу:
            1. Понять, что именно нужно сделать
            2. Использовать соответствующие инструменты для получения данных
            3. Проанализировать данные
            4. Предоставить понятный ответ пользователю
            5. Если пользователь просит отправить отчет в Telegram, использовать инструмент sendTelegramMessage
            
            Отвечай на русском языке, будь дружелюбным и понятным. Используй инструменты для выполнения задач пользователя.
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

        // Создаем ToolRegistry (пока пустой, инструменты будут добавлены позже)
        // Используем EMPTY для начала, инструменты добавим позже
        val toolRegistry = ToolRegistry.EMPTY
        
        logger.info("Creating AIAgent with ToolRegistry")

        return AIAgent(
            promptExecutor = executor,
            llmModel = model,
            systemPrompt = systemPrompt,
            toolRegistry = toolRegistry
        )
    }
}

