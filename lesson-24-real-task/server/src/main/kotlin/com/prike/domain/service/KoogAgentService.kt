package com.prike.domain.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import com.prike.config.KoogConfig
import com.prike.domain.tools.ReviewsTools
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
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

        // Создаем ToolRegistry с инструментами из ReviewsTools
        val toolRegistry = createToolRegistry()
        
        logger.info("Creating AIAgent with ToolRegistry (${toolRegistry.tools.size} tools)")

        return AIAgent(
            promptExecutor = executor,
            llmModel = model,
            systemPrompt = systemPrompt,
            toolRegistry = toolRegistry
        )
    }

    /**
     * Создает ToolRegistry с инструментами из ReviewsTools
     */
    private fun createToolRegistry(): ToolRegistry {
        // Создаем список всех инструментов
        val tools = mutableListOf<Tool<*, *>>(
            createFetchReviewsTool(),
            createSaveReviewSummariesTool(),
            createGetWeekSummariesTool(),
            createGetAllSummariesTool(),
            createCalculateWeekDatesTool(),
            createGetWeekStatsTool(),
            createGetPreviousWeekStatsTool()
        )
        
        // Инструмент для отправки в Telegram (если доступен)
        try {
            val telegramField = ReviewsTools::class.java.getDeclaredField("telegramMCPClient")
            telegramField.isAccessible = true
            val telegramClient = telegramField.get(reviewsTools)
            if (telegramClient != null) {
                tools.add(createSendTelegramMessageTool())
            }
        } catch (e: Exception) {
            logger.debug("Telegram MCP client not available: ${e.message}")
        }
        
        // Создаем ToolRegistry через Companion.invoke с правильным синтаксисом
        return ToolRegistry.Companion.invoke(fun(builder: ToolRegistry.Builder) {
            tools.forEach { tool ->
                builder.tool(tool)
            }
        })
    }

    // Вспомогательные классы для параметров инструментов
    @Serializable
    data class FetchReviewsArgs(
        val fromDate: String,
        val toDate: String,
        val limit: Int = 100
    )

    @Serializable
    data class SaveReviewSummariesArgs(
        val summariesJson: String,
        val weekStart: String
    )

    @Serializable
    data class GetWeekSummariesArgs(
        val weekStart: String
    )

    @Serializable
    data class CalculateWeekDatesArgs(
        val dateStr: String? = null
    )

    @Serializable
    data class GetWeekStatsArgs(
        val weekStart: String
    )

    @Serializable
    data class GetPreviousWeekStatsArgs(
        val currentWeekStart: String
    )

    @Serializable
    data class SendTelegramMessageArgs(
        val message: String
    )

    private fun createFetchReviewsTool(): Tool<FetchReviewsArgs, String> {
        return object : SimpleTool<FetchReviewsArgs>() {
            override val name: String = "fetchReviews"
            override val description: String = "Получает отзывы из API за указанный период. Параметры: fromDate (ISO8601), toDate (ISO8601), limit (по умолчанию 100)"
            override val argsSerializer = serializer<FetchReviewsArgs>()
            
            override suspend fun doExecute(args: FetchReviewsArgs): String {
                return reviewsTools.fetchReviews(args.fromDate, args.toDate, args.limit)
            }
        }
    }

    private fun createSaveReviewSummariesTool(): Tool<SaveReviewSummariesArgs, String> {
        return object : SimpleTool<SaveReviewSummariesArgs>() {
            override val name: String = "saveReviewSummaries"
            override val description: String = "Сохраняет саммари отзывов в базу данных. Параметры: summariesJson (JSON строка с массивом саммари), weekStart (ISO8601)"
            override val argsSerializer = serializer<SaveReviewSummariesArgs>()
            
            override suspend fun doExecute(args: SaveReviewSummariesArgs): String {
                return reviewsTools.saveReviewSummaries(args.summariesJson, args.weekStart)
            }
        }
    }

    private fun createGetWeekSummariesTool(): Tool<GetWeekSummariesArgs, String> {
        return object : SimpleTool<GetWeekSummariesArgs>() {
            override val name: String = "getWeekSummaries"
            override val description: String = "Получает саммари отзывов за указанную неделю. Параметры: weekStart (ISO8601)"
            override val argsSerializer = serializer<GetWeekSummariesArgs>()
            
            override suspend fun doExecute(args: GetWeekSummariesArgs): String {
                return reviewsTools.getWeekSummaries(args.weekStart)
            }
        }
    }

    private fun createGetAllSummariesTool(): Tool<Unit, String> {
        return object : SimpleTool<Unit>() {
            override val name: String = "getAllSummaries"
            override val description: String = "Получает все саммари отзывов из базы данных"
            override val argsSerializer = serializer<Unit>()
            
            override suspend fun doExecute(args: Unit): String {
                return reviewsTools.getAllSummaries()
            }
        }
    }

    private fun createCalculateWeekDatesTool(): Tool<CalculateWeekDatesArgs, String> {
        return object : SimpleTool<CalculateWeekDatesArgs>() {
            override val name: String = "calculateWeekDates"
            override val description: String = "Вычисляет даты начала и конца недели для указанной даты. Параметры: dateStr (ISO8601, опционально, по умолчанию текущая дата)"
            override val argsSerializer = serializer<CalculateWeekDatesArgs>()
            
            override suspend fun doExecute(args: CalculateWeekDatesArgs): String {
                return reviewsTools.calculateWeekDates(args.dateStr)
            }
        }
    }

    private fun createGetWeekStatsTool(): Tool<GetWeekStatsArgs, String> {
        return object : SimpleTool<GetWeekStatsArgs>() {
            override val name: String = "getWeekStats"
            override val description: String = "Получает статистику за указанную неделю. Параметры: weekStart (ISO8601)"
            override val argsSerializer = serializer<GetWeekStatsArgs>()
            
            override suspend fun doExecute(args: GetWeekStatsArgs): String {
                return reviewsTools.getWeekStats(args.weekStart) ?: "{\"error\": \"Week stats not found\"}"
            }
        }
    }

    private fun createGetPreviousWeekStatsTool(): Tool<GetPreviousWeekStatsArgs, String> {
        return object : SimpleTool<GetPreviousWeekStatsArgs>() {
            override val name: String = "getPreviousWeekStats"
            override val description: String = "Получает статистику предыдущей недели. Параметры: currentWeekStart (ISO8601)"
            override val argsSerializer = serializer<GetPreviousWeekStatsArgs>()
            
            override suspend fun doExecute(args: GetPreviousWeekStatsArgs): String {
                return reviewsTools.getPreviousWeekStats(args.currentWeekStart)
            }
        }
    }

    private fun createSendTelegramMessageTool(): Tool<SendTelegramMessageArgs, String> {
        return object : SimpleTool<SendTelegramMessageArgs>() {
            override val name: String = "sendTelegramMessage"
            override val description: String = "Отправляет сообщение в Telegram через MCP. Параметры: message (текст сообщения)"
            override val argsSerializer = serializer<SendTelegramMessageArgs>()
            
            override suspend fun doExecute(args: SendTelegramMessageArgs): String {
                return reviewsTools.sendTelegramMessage(args.message)
            }
        }
    }
}

