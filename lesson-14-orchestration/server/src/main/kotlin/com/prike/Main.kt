package com.prike

import com.prike.config.Config
import com.prike.data.client.MCPClientManager
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.MCPToolAgent
import com.prike.domain.agent.OrchestrationAgent
import com.prike.presentation.controller.ChatController
import com.prike.presentation.controller.ClientController
import com.prike.presentation.controller.ConnectionController
import com.prike.presentation.controller.MCPController
import com.prike.presentation.controller.ToolController
import com.prike.presentation.controller.WebSocketChatController
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.websocket.*
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
    
    install(io.ktor.server.websocket.WebSockets) {
        // Настройки WebSocket
    }

    val logger = LoggerFactory.getLogger("Application")
    
    // Находим корень урока для определения путей к MCP серверам
    val lessonRoot = findLessonRoot()
    logger.info("Lesson root: ${lessonRoot.absolutePath}")
    
    // Создание MCP Client Manager
    val mcpClientManager = MCPClientManager(config.mcp, lessonRoot)
    
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
    
    // Создание Orchestration Agent
    val orchestrationAgent = OrchestrationAgent(
        aiRepository = aiRepository,
        mcpToolAgent = mcpToolAgent
    )
    
    // Автоматическое подключение к MCP серверам при старте
    launch {
        try {
            logger.info("Connecting to MCP servers...")
            mcpClientManager.initialize()
            logger.info("MCP servers connected successfully")
        } catch (e: Exception) {
            logger.error("Failed to connect to MCP servers: ${e.message}", e)
        }
    }
    
    // Регистрация shutdown hook для корректного отключения
    environment.monitor.subscribe(ApplicationStopped) {
        runBlocking {
            try {
                mcpClientManager.disconnectAll()
            } catch (e: Exception) {
                logger.error("Error during shutdown: ${e.message}", e)
            }
        }
    }
    
    // Регистрация контроллеров
    val clientDir = File(lessonRoot, "client")
    val clientController = ClientController(clientDir)
    val mcpController = MCPController(mcpClientManager)
    val toolController = ToolController(mcpClientManager, config.mcp)
    val connectionController = ConnectionController(mcpClientManager)
    val chatController = ChatController(orchestrationAgent)
    val webSocketChatController = WebSocketChatController(orchestrationAgent)
    
    routing {
        // Статические файлы для UI
        clientController.registerRoutes(this)
        
        // WebSocket чат
        webSocketChatController.registerRoutes(this)
        
        // Health check API
        get("/api/health") {
            call.respond(mapOf(
                "status" to "ok",
                "service" to "lesson-14-orchestration-server"
            ))
        }
        
        // API routes
        mcpController.registerRoutes(this)
        toolController.registerRoutes(this)
        connectionController.registerRoutes(this)
        chatController.registerRoutes(this)
    }
    
    // Закрытие ресурсов при остановке
    environment.monitor.subscribe(ApplicationStopped) {
        openAIClient.close()
    }
    
    logger.info("Server started on ${config.server.host}:${config.server.port}")
}

/**
 * Находит корень урока (lesson-14-orchestration)
 */
private fun findLessonRoot(): File {
    var currentDir = File(System.getProperty("user.dir"))
    
    if (currentDir.name == "server") {
        currentDir = currentDir.parentFile
    }
    
    // Ищем lesson-14-orchestration вверх по дереву
    var searchDir = currentDir
    while (searchDir != null && searchDir.parentFile != null) {
        if (searchDir.name == "lesson-14-orchestration") {
            return searchDir
        }
        
        val lessonDir = File(searchDir, "lesson-14-orchestration")
        if (lessonDir.exists()) {
            return lessonDir
        }
        
        searchDir = searchDir.parentFile
    }
    
    // Если не нашли, возвращаем текущую директорию
    return currentDir
}

