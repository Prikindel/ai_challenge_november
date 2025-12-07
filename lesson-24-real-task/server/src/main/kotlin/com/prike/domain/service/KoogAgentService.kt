package com.prike.domain.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
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

        logger.info("Initializing Koog AIAgent with model: ${koogConfig.model}, useOpenRouter: ${koogConfig.useOpenRouter}")

        // Получаем текущую дату для включения в промпт
        val currentDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_DATE)
        val currentDateTime = java.time.Instant.now().atOffset(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        
        val systemPrompt = """
            Ты - умный помощник для анализа отзывов пользователей мобильных приложений.
            
            ВАЖНО: Текущая дата: $currentDate (ISO: $currentDateTime)
            Используй эту дату для определения актуальных периодов при запросах отзывов.
            
            Твоя задача - помогать пользователю анализировать отзывы, сравнивать статистику и генерировать отчеты.
            
            У тебя есть доступ к инструментам для:
            - getCurrentDate - Получения текущей даты и времени (используй, если нужно узнать актуальную дату)
            - fetchReviews - Получения отзывов из API за указанный период (параметры: fromDate, toDate в формате ISO8601, limit)
            - saveReviewSummaries - Сохранения саммари отзывов в базу данных
            - getWeekSummaries - Получения саммари отзывов за неделю
            - getAllSummaries - Получения всех саммари отзывов
            - getWeekStats - Получения статистики по неделям
            - calculateWeekDates - Вычисления дат начала и конца недели
            - sendTelegramMessage - Отправки сообщений в Telegram (если настроено)
            - indexReviewSummary - Индексации саммари для RAG
            - analyzePeriodByDays - Анализ отзывов за период с автоматическим батчингом по дням (разбивает период на дни, для каждого дня получает отзывы, анализирует через LLM и сохраняет в БД)
            
            ВАЖНО: Когда пользователь просит получить отзывы или отправить отчет, ТЫ ДОЛЖЕН автоматически:
            1. Если нужно узнать текущую дату, вызови getCurrentDate
            2. Если нужно найти отзывы в БД:
               - Сначала попробуй getWeekSummaries с weekStart (дата начала недели в формате "2025-12-01")
               - Если не найдено, используй getAllSummaries для получения всех отзывов из БД
               - Если в БД есть отзывы, используй их для формирования отчета
            3. Если нужно получить новые отзывы из API:
               - Определи период (если не указан, используй последние 30 дней от ТЕКУЩЕЙ ДАТЫ или текущую неделю)
               - Вызови инструмент fetchReviews с правильными датами в формате ISO8601 (например: "2024-12-07T00:00:00Z")
               - ВАЖНО: 
                 * Если пользователь явно указал количество (например, "10 отзывов"), используй это количество
                 * Если пользователь просит "все отзывы", используй limit=100-200 (разумный баланс между полнотой и стоимостью)
                 * Если количество не указано, используй limit=50 для быстрого и экономичного анализа
                 * Система автоматически делает пагинацию по 50 отзывов, если limit >= 50
                 * НЕ используй limit больше 200 без явного запроса пользователя - это дорого и долго
               - Проанализируй полученные отзывы и создай саммари
               - Сохрани саммари через saveReviewSummaries
            4. Предоставь понятный ответ пользователю
            
            КРИТИЧЕСКИ ВАЖНО: При сохранении саммари через saveReviewSummaries:
            1. Передавай ТОЛЬКО валидный JSON массив, без дополнительного текста, markdown разметки или комментариев
            2. НЕ добавляй текст после JSON (например, "Goodbye now!" или эмодзи)
            3. JSON должен быть полным и завершенным (не обрезанным)
            4. Структура каждого объекта ReviewSummary:
            {
              "reviewId": "string (ID отзыва из Review.id)",
              "rating": 1-5 (число из Review.rating),
              "date": "ISO8601 строка из Review.date",
              "summary": "краткое саммари отзыва (строка, 1-2 предложения, БЕЗ markdown разметки)",
              "category": "POSITIVE" | "NEGATIVE" | "NEUTRAL",
              "topics": ["AUTO_UPLOAD" | "ALBUMS" | "UNLIMITED" | "CRASHES" | "DOCUMENTS" | "OTHER" | "DOWNLOAD_MANAGER" | "LOW_SPEED" | "OPERATIONS" | "FILE_OPERATIONS" | "STORAGE_DISPLAY" | "OFFLINE" | "UI_ERRORS" | "RESOURCE_USAGE" | "LINK_SETTINGS" | "MISSING_FILES" | "PHOTO_VIDEO_VIEWER" | "VIEWER" | "MANUAL_UPLOAD" | "SCANNER" | "DOWNLOAD" | "INSTALLATION" | "PHOTO_SLICE"],
              "criticality": "HIGH" | "MEDIUM" | "LOW",
              "weekStart": "ISO8601 строка (опционально)"
            }
            
            topics должен быть массивом строк с именами enum значений ReviewTopic (например: ["MISSING_FILES", "LOW_SPEED"]).
            category определяется по rating: 4-5 = POSITIVE, 1-2 = NEGATIVE, 3 = NEUTRAL.
            criticality: HIGH для негативных отзывов с rating 1, MEDIUM для rating 2, LOW для остальных.
            
            ПРИМЕР правильного вызова saveReviewSummaries:
            summariesJson: "[{\"reviewId\":\"123\",\"rating\":5,\"date\":\"2025-12-07T00:00:00Z\",\"summary\":\"Отличное приложение\",\"category\":\"POSITIVE\",\"topics\":[\"OTHER\"],\"criticality\":\"LOW\"}]"
            weekStart: "2025-12-01"
            
            НЕ используй старые даты (например, 2024-01-01) - всегда используй актуальные даты относительно текущей даты!
            НЕ спрашивай пользователя о периоде, если он не указан явно - используй разумные значения по умолчанию (последние 30 дней или текущая неделя).
            
            Отвечай на русском языке, будь дружелюбным и понятным. ВСЕГДА используй инструменты для выполнения задач пользователя.
        """.trimIndent()

        // Определяем, используется ли OpenRouter по формату ключа или настройке
        val isOpenRouterKey = koogConfig.apiKey.startsWith("sk-or-v1")
        val useOpenRouter = isOpenRouterKey || koogConfig.useOpenRouter
        
        val executor = if (useOpenRouter) {
            logger.info("Using OpenRouter executor")
            simpleOpenRouterExecutor(koogConfig.apiKey)
        } else {
            logger.info("Using OpenAI executor")
            simpleOpenAIExecutor(koogConfig.apiKey)
        }

        val model = OpenRouterModels.GPT4o
//        val model = LLModel(
//            provider = LLMProvider.OpenRouter,
//            id = "qwen/qwen3-coder-30b-a3b-instruct",
//            capabilities =  listOf(
//                LLMCapability.Temperature,
//                LLMCapability.Speculation,
//                LLMCapability.Tools,
//                LLMCapability.Completion,
//                LLMCapability.Vision.Image,
//                LLMCapability.Schema.JSON.Standard,
//                LLMCapability.ToolChoice
//            ),
//            contextLength = 128_000,
//            maxOutputTokens = 4000,
//        )
            /*if (useOpenRouter) {
            // Для OpenRouter используем модели OpenRouter
            // OpenRouter использует формат "openai/gpt-4o" или просто "gpt-4o"
            when (koogConfig.model.lowercase()) {
                "gpt-4o-mini", "gpt4o-mini", "openai/gpt-4o-mini" -> {
                    logger.info("Using OpenRouter model: openai/gpt-4o-mini")
                    OpenRouterModels.GPT4oMini
                }
                "gpt-4o", "gpt4o", "openai/gpt-4o" -> {
                    logger.info("Using OpenRouter model: openai/gpt-4o")
                    OpenRouterModels.GPT4o
                }
                "gpt-4-turbo", "gpt4-turbo", "openai/gpt-4-turbo" -> {
                    logger.info("Using OpenRouter model: openai/gpt-4-turbo")
                    OpenRouterModels.GPT4Turbo
                }
                else -> {
                    logger.warn("Unknown model ${koogConfig.model} for OpenRouter, using GPT4o")
                    OpenRouterModels.GPT4o
                }
            }
        } else {
            logger.warn("Unknown model ${koogConfig.model} for OpenAI, using GPT4o")
            OpenAIModels.Chat.GPT4o
        }*/

        // Создаем ToolRegistry с инструментами из ReviewsTools
        val toolRegistry = createToolRegistry()
        
        logger.info("Creating AIAgent with ToolRegistry (${toolRegistry.tools.size} tools)")

        return AIAgent(
            promptExecutor = executor,
            llmModel = model,
            systemPrompt = systemPrompt,
            toolRegistry = toolRegistry,
            installFeatures = {
                try {
                    // Используем OpenTelemetry с LoggingSpanExporter для логирования LLM вызовов
                    val openTelemetryClass = Class.forName("ai.koog.agents.core.feature.OpenTelemetry")
                    val loggingExporterClass = Class.forName("io.opentelemetry.exporter.logging.LoggingSpanExporter")
                    val createMethod = loggingExporterClass.getMethod("create")
                    val exporter = createMethod.invoke(null)
                    
                    val installMethod = openTelemetryClass.getMethod("install", 
                        kotlin.jvm.functions.Function1::class.java)
                    
                    val configLambda: (Any) -> Unit = { config ->
                        val addSpanExporterMethod = config.javaClass.getMethod("addSpanExporter",
                            Class.forName("io.opentelemetry.sdk.trace.export.SpanExporter"))
                        addSpanExporterMethod.invoke(config, exporter)
                    }
                    
                    installMethod.invoke(null, configLambda)
                    logger.info("OpenTelemetry with LoggingSpanExporter installed for LLM tracing")
                } catch (e: Exception) {
                    logger.debug("OpenTelemetry not available: ${e.message}")
                    // Продолжаем без OpenTelemetry, если он недоступен
                }
            }
        )
    }

    /**
     * Создает ToolRegistry с инструментами из ReviewsTools
     */
    private fun createToolRegistry(): ToolRegistry {
        // Создаем список всех инструментов
        val tools = mutableListOf<Tool<*, *>>(
            createGetCurrentDateTool(),
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

    private fun createGetCurrentDateTool(): Tool<Unit, String> {
        return object : SimpleTool<Unit>() {
            override val name: String = "getCurrentDate"
            override val description: String = "Получает текущую дату и время в формате ISO8601. Не требует параметров. Используй этот инструмент, если нужно узнать актуальную дату для определения периодов запросов."
            override val argsSerializer = serializer<Unit>()
            
            override suspend fun doExecute(args: Unit): String {
                return reviewsTools.getCurrentDate()
            }
        }
    }

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

