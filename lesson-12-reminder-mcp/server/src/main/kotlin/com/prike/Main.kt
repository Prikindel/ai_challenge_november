package com.prike

import com.prike.config.Config
import com.prike.data.client.MCPClientManager
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepository
import com.prike.data.repository.SummaryRepository
import com.prike.domain.agent.LLMWithSummaryAgent
import com.prike.domain.agent.MCPToolAgent
import com.prike.domain.service.SchedulerConfig
import com.prike.domain.service.SchedulerService
import com.prike.presentation.controller.ChatController
import com.prike.presentation.controller.ClientController
import com.prike.presentation.controller.ConnectionController
import com.prike.presentation.controller.SummaryController
import com.prike.presentation.controller.ToolController
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.io.File

/**
 * Точка входа приложения
 */
fun main() {
    val config = Config.load()
    
    embeddedServer(Netty, port = config.server.port, host = config.server.host) {
        module(config)
    }.start(wait = true)
}

/**
 * Настройка Ktor приложения
 */
fun Application.module(config: com.prike.config.AppConfig) {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost()
    }

    install(ContentNegotiation) {
        json()
    }

    install(CallLogging) {
        level = Level.INFO
    }

    val logger = LoggerFactory.getLogger("Application")
    
    // Находим корень урока для определения путей к MCP серверам
    val lessonRoot = findLessonRoot()
    logger.info("Lesson root: ${lessonRoot.absolutePath}")
    
    // Создание MCP Client Manager
    val mcpClientManager = MCPClientManager(config, lessonRoot)
    
    // Создание LLM клиента и репозитория
    val openAIClient = OpenAIClient(
        apiKey = config.ai.apiKey,
        model = config.ai.model,
        temperature = 0.7,
        maxTokens = 2000
    )
    val aiRepository = AIRepository(openAIClient)
    
    // Создание MCP Tool Agent
    val mcpToolAgent = MCPToolAgent(mcpClientManager)
    
    // Создание LLM агента с поддержкой MCP инструментов
    val systemPrompt = """
        Ты полезный AI-ассистент с доступом к инструментам для получения данных из разных источников.
        
        Доступные источники данных:
        - Web Chat: история переписки из веб-чата (инструмент get_chat_history)
        - Telegram: сообщения из Telegram группы (инструмент get_telegram_messages)
        
        Когда пользователь просит проанализировать данные или получить информацию из источника,
        используй соответствующий инструмент. После получения данных, проанализируй их и предоставь
        понятное резюме на русском языке.
        
        Отвечай кратко и по делу. Используй Markdown для форматирования ответов.
    """.trimIndent()
    
    val llmAgent = LLMWithSummaryAgent(
        aiRepository = aiRepository,
        mcpToolAgent = mcpToolAgent,
        systemPrompt = systemPrompt
    )
    
    // Создание SummaryRepository
    val summaryDbPath = File(lessonRoot, "data/summary.db").absolutePath
    val summaryRepository = SummaryRepository(summaryDbPath)
    
    // Валидация конфигурации планировщика
    val activeSource = config.scheduler.activeSource
    val activeSourceConfig = config.dataSources[activeSource]
    if (activeSourceConfig == null) {
        logger.warn("Active source '$activeSource' not found in dataSources. Available sources: ${config.dataSources.keys.joinToString()}")
    } else if (!activeSourceConfig.enabled) {
        logger.warn("Active source '$activeSource' is disabled in configuration. Scheduler may fail to generate summaries.")
    }
    
    // Создание SchedulerService (использует MCP для отправки в Telegram)
    val schedulerConfig = SchedulerConfig(
        enabled = config.scheduler.enabled,
        intervalMinutes = config.scheduler.intervalMinutes,
        periodHours = config.scheduler.periodHours,
        activeSource = activeSource,
        telegramDeliveryEnabled = config.scheduler.delivery.telegram.enabled,
        telegramUserId = config.scheduler.delivery.telegram.userId
    )
    val schedulerService = SchedulerService(
        llmAgent = llmAgent,
        summaryRepository = summaryRepository,
        mcpClientManager = mcpClientManager,
        config = schedulerConfig
    )
    
    // Автоматическое подключение к MCP серверам при старте, затем запуск планировщика
    launch {
        try {
            logger.info("Connecting to MCP servers...")
            mcpClientManager.connectAll()
            logger.info("MCP servers connected successfully")
            
            // Запускаем планировщик только после подключения MCP серверов
            if (config.scheduler.enabled) {
                // Проверяем, что активный источник подключен
                val connectedSources = mcpClientManager.getConnectedSources()
                if (activeSource in connectedSources) {
                    schedulerService.start()
                    logger.info("Scheduler started: first summary in ${config.scheduler.intervalMinutes} minutes, then every ${config.scheduler.intervalMinutes} minutes")
                } else {
                    logger.error(
                        "Cannot start scheduler: active source '$activeSource' is not connected. " +
                        "Connected sources: ${connectedSources.joinToString()}. " +
                        "Make sure the source is enabled in configuration."
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to connect to MCP servers: ${e.message}", e)
            if (config.scheduler.enabled) {
                logger.warn("Scheduler will not start due to MCP connection failure")
            }
        }
    }
    
    // Регистрация shutdown hook для корректного отключения
    environment.monitor.subscribe(ApplicationStopped) {
        runBlocking {
            try {
                schedulerService.stop()
                mcpClientManager.disconnectAll()
            } catch (e: Exception) {
                logger.error("Error during shutdown: ${e.message}", e)
            }
        }
    }
    
    // Регистрация контроллеров
    val clientDir = File(lessonRoot, "client")
    val clientController = ClientController(clientDir)
    val toolController = ToolController(mcpClientManager)
    val connectionController = ConnectionController(mcpClientManager)
    val chatController = ChatController(llmAgent)
    val summaryController = SummaryController(summaryRepository, schedulerService)
    
    routing {
        // Статические файлы для UI
        clientController.registerRoutes(this)
        
        // Health check API
        get("/api/health") {
            call.respond(mapOf(
                "status" to "ok",
                "service" to "lesson-12-reminder-mcp-server"
            ))
        }
        
        // API routes
        toolController.registerRoutes(this)
        connectionController.registerRoutes(this)
        chatController.registerRoutes(this)
        summaryController.registerRoutes(this)
    }
    
    // Закрытие ресурсов при остановке
    environment.monitor.subscribe(ApplicationStopped) {
        openAIClient.close()
    }
    
    logger.info("Server started on ${config.server.host}:${config.server.port}")
}

/**
 * Находит корень урока (lesson-12-reminder-mcp)
 */
private fun findLessonRoot(): File {
    var currentDir = File(System.getProperty("user.dir"))
    
    if (currentDir.name == "server") {
        currentDir = currentDir.parentFile
    }
    
    // Ищем lesson-12-reminder-mcp вверх по дереву
    var searchDir = currentDir
    while (searchDir != null && searchDir.parentFile != null) {
        if (searchDir.name == "lesson-12-reminder-mcp") {
            return searchDir
        }
        
        val lessonDir = File(searchDir, "lesson-12-reminder-mcp")
        if (lessonDir.exists()) {
            return lessonDir
        }
        
        searchDir = searchDir.parentFile
    }
    
    // Если не нашли, возвращаем текущую директорию
    return currentDir
}

