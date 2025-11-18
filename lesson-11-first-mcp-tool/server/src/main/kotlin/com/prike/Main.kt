package com.prike

import com.prike.config.ConfigLoader
import com.prike.config.MCPConfig
import com.prike.config.MCPConfigLoader
import com.prike.data.client.MCPClient
import com.prike.data.client.OpenAIClient
import com.prike.data.repository.AIRepository
import com.prike.domain.agent.LLMWithMCPAgent
import com.prike.domain.agent.MCPToolAgent
import com.prike.presentation.controller.ChatController
import com.prike.presentation.controller.ClientController
import com.prike.presentation.controller.MCPConnectionController
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
import java.io.File
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch

/**
 * Точка входа приложения
 */
fun main() {
    embeddedServer(Netty, port = Config.serverPort, host = Config.serverHost) {
        module()
    }.start(wait = true)
}

/**
 * Настройка Ktor приложения
 */
fun Application.module() {
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
    
    // Находим корень урока для загрузки конфигурации
    val lessonRoot = Config.getLessonRoot()
    
    // Загрузка конфигурации AI
    val aiConfig = try {
        ConfigLoader.loadAIConfig(lessonRoot)
    } catch (e: Exception) {
        logger.error("Failed to load AI config: ${e.message}", e)
        throw e
    }
    
    // Загрузка конфигурации MCP
    val mcpConfig = try {
        MCPConfigLoader.loadMCPConfig(lessonRoot)
    } catch (e: Exception) {
        logger.warn("Failed to load MCP config, using defaults: ${e.message}")
        MCPConfig()
    }
    
    // Создание зависимостей для MCP
    val mcpClient = MCPClient()
    val mcpToolAgent = MCPToolAgent(mcpClient)
    
    // Автоматический запуск MCP сервера при старте приложения (если настроено)
    if (mcpConfig.autoStart) {
        launch {
            try {
                if (mcpClient.isConnected()) {
                    logger.info("MCP server is already connected (auto-start skipped)")
                } else {
                    logger.info("Auto-starting MCP server...")
                    mcpClient.connectToServer(mcpConfig.serverJarPath)
                    logger.info("MCP server started successfully")
                }
            } catch (e: Exception) {
                logger.error("Failed to auto-start MCP server: ${e.message}", e)
            }
        }
    }
    
    // Создание зависимостей для LLM
    val openAIClient = OpenAIClient(
        apiKey = aiConfig.apiKey,
        apiUrl = aiConfig.apiUrl,
        model = aiConfig.model,
        temperature = aiConfig.temperature,
        maxTokens = aiConfig.maxTokens,
        requestTimeoutSeconds = aiConfig.requestTimeout,
        systemPrompt = aiConfig.systemPrompt
    )
    val aiRepository = AIRepository(openAIClient)
    val llmWithMCPAgent = LLMWithMCPAgent(
        aiRepository = aiRepository,
        mcpToolAgent = mcpToolAgent,
        systemPrompt = aiConfig.systemPrompt ?: """
            Ты полезный AI-ассистент с доступом к инструментам Telegram Bot API.
            Когда пользователь просит отправить сообщение или получить информацию о боте, 
            используй доступные инструменты MCP.
            Отвечай кратко и по делу. Используй Markdown для форматирования ответов.
        """.trimIndent()
    )
    
    // Создание контроллеров
    val clientDir = File(lessonRoot, "client")
    val clientController = ClientController(clientDir)
    val mcpConnectionController = MCPConnectionController(mcpClient, lessonRoot)
    val toolController = ToolController(mcpToolAgent)
    val chatController = ChatController(llmWithMCPAgent)

    routing {
        // Статические файлы клиента
        clientController.configureRoutes(this)
        
        // API маршруты
        mcpConnectionController.configureRoutes(this)
        toolController.configureRoutes(this)
        chatController.configureRoutes(this)
    }

    environment.monitor.subscribe(ApplicationStopped) {
        runBlocking {
            mcpClient.disconnect()
        }
        openAIClient.close()
    }
}

