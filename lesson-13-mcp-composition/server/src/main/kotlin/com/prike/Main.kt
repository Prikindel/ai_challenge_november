package com.prike

import com.prike.config.Config
import com.prike.data.client.MCPClientManager
import com.prike.presentation.controller.ConnectionController
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
    val mcpClientManager = MCPClientManager(config.mcp, lessonRoot)
    
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
    val toolController = ToolController(mcpClientManager, config.mcp)
    val connectionController = ConnectionController(mcpClientManager)
    
    routing {
        // Health check API
        get("/api/health") {
            call.respond(mapOf(
                "status" to "ok",
                "service" to "lesson-13-mcp-composition-server"
            ))
        }
        
        // API routes
        toolController.registerRoutes(this)
        connectionController.registerRoutes(this)
    }
    
    logger.info("Server started on ${config.server.host}:${config.server.port}")
}

/**
 * Находит корень урока (lesson-13-mcp-composition)
 */
private fun findLessonRoot(): File {
    var currentDir = File(System.getProperty("user.dir"))
    
    if (currentDir.name == "server") {
        currentDir = currentDir.parentFile
    }
    
    // Ищем lesson-13-mcp-composition вверх по дереву
    var searchDir = currentDir
    while (searchDir != null && searchDir.parentFile != null) {
        if (searchDir.name == "lesson-13-mcp-composition") {
            return searchDir
        }
        
        val lessonDir = File(searchDir, "lesson-13-mcp-composition")
        if (lessonDir.exists()) {
            return lessonDir
        }
        
        searchDir = searchDir.parentFile
    }
    
    // Если не нашли, возвращаем текущую директорию
    return currentDir
}

