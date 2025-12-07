package com.prike

import com.prike.config.Config
import com.prike.data.DatabaseManager
import com.prike.data.client.TelegramMCPClient
import com.prike.data.repository.ChatRepository
import com.prike.data.repository.ReviewsRepository
import com.prike.domain.agent.ReviewsAnalyzerAgent
import com.prike.domain.service.KoogAgentService
import com.prike.domain.service.ReviewsAnalysisService
import com.prike.domain.service.ReviewsChatService
import com.prike.infrastructure.client.ReviewsApiClient
import com.prike.presentation.controller.ChatController
import com.prike.presentation.controller.ClientController
import com.prike.presentation.controller.ReviewsController
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("Main")
    
    val config = Config.load()
    val lessonRoot = findLessonRoot()
    
    logger.info("Starting lesson-24-real-task server...")
    logger.info("Lesson root: ${lessonRoot.absolutePath}")
    
    // Инициализация БД
    val databaseManager = DatabaseManager(config.database, lessonRoot)
    databaseManager.init()
    val database = databaseManager.database
    
    
    // Инициализация Telegram MCP Client (если включен)
    val telegramMCPClient = TelegramMCPClient(config.telegram, lessonRoot)
    if (config.telegram.mcp.enabled) {
        runBlocking {
            try {
                telegramMCPClient.connect()
                logger.info("Telegram MCP client connected")
            } catch (e: Exception) {
                logger.error("Failed to connect Telegram MCP client: ${e.message}", e)
            }
        }
    }
    
    // Инициализация HTTP клиента для API
    val httpClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    // Инициализация компонентов
    val reviewsApiClient = ReviewsApiClient(config.reviews.api.baseUrl, httpClient)
    val reviewsRepository = ReviewsRepository(database)
    val reviewsTools = com.prike.domain.tools.ReviewsTools(
        apiClient = reviewsApiClient,
        repository = reviewsRepository,
        reviewsConfig = config.reviews,
        telegramMCPClient = if (config.telegram.mcp.enabled) telegramMCPClient else null,
        telegramConfig = if (config.telegram.mcp.enabled) config.telegram else null
    )
    
    // Инициализация Koog Agent Service с инструментами
    val koogAgentService = KoogAgentService(config.koog, reviewsTools)
    val koogAgent = koogAgentService.createAgent()
    logger.info("Koog AIAgent initialized")
    
    val reviewsAnalyzerAgent = ReviewsAnalyzerAgent(
        koogAgent = koogAgent,
        reviewsConfig = config.reviews,
        apiClient = reviewsApiClient,
        repository = reviewsRepository
    )
    val reviewsAnalysisService = ReviewsAnalysisService(reviewsAnalyzerAgent)
    val reviewsController = ReviewsController(reviewsAnalysisService)
    
    // Инициализация ChatRepository и ChatService
    val chatRepository = ChatRepository(database)
    val reviewsChatService = ReviewsChatService(chatRepository, koogAgent)
    val chatController = ChatController(reviewsChatService, chatRepository)
    
    // Статический контент для UI
    val clientDir = File(lessonRoot, "client")
    val clientController = ClientController(clientDir)
    
    embeddedServer(Netty, port = config.server.port, host = config.server.host) {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
            })
        }
        
        // Настройка CORS для работы с фронтендом
        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            anyHost()
        }
        
        routing {
            // Статические файлы для UI
            clientController.registerRoutes(this)
            
            // Health check API
            get("/api/health") {
                call.respond(HttpStatusCode.OK, HealthResponse("ok", "lesson-24-real-task-server"))
            }
            
            // API маршруты для работы с отзывами
            reviewsController.registerRoutes(this)
            
            // API маршруты для чата
            chatController.registerRoutes(this)
        }
    }.start(wait = true)
}

@Serializable
data class HealthResponse(val status: String, val service: String)

/**
 * Находит корень урока (lesson-24-real-task)
 */
private fun findLessonRoot(): File {
    var currentDir = File(System.getProperty("user.dir"))
    
    if (currentDir.name == "server") {
        currentDir = currentDir.parentFile
    }
    
    // Ищем lesson-24-real-task вверх по дереву
    var searchDir: File? = currentDir
    while (searchDir != null) {
        if (searchDir.name == "lesson-24-real-task") {
            return searchDir
        }
        
        val lessonDir = File(searchDir, "lesson-24-real-task")
        if (lessonDir.exists()) {
            return lessonDir
        }
        
        searchDir = searchDir.parentFile
    }
    
    // Если не нашли, возвращаем текущую директорию
    return currentDir
}

