package com.prike

import com.prike.config.Config
import com.prike.data.DatabaseManager
import com.prike.data.client.TelegramMCPClient
import com.prike.data.client.TelegramMCPClientAdapter
import com.prike.data.client.MCPClientInterface
import com.prike.data.repository.ChatRepository
import com.prike.data.repository.ReviewsRepository
import com.prike.domain.agent.ReviewsAnalyzerAgent
import com.prike.domain.service.AudioConversionService
import com.prike.domain.service.EmbeddingService
import com.prike.domain.service.KoogAgentService
import com.prike.domain.service.ReviewSummaryRagService
import com.prike.domain.service.ReviewsAnalysisService
import com.prike.domain.service.ReviewsChatService
import com.prike.domain.service.SpeechRecognitionService
import com.prike.infrastructure.client.ReviewsApiClient
import com.prike.presentation.controller.ChatController
import com.prike.presentation.controller.ClientController
import com.prike.presentation.controller.ProfileController
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
    
    logger.info("Starting lesson-32-god-agent server...")
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
           val reviewsApiClient = ReviewsApiClient(
               baseUrl = config.reviews.api.baseUrl,
               httpClient = httpClient,
               oauthToken = config.reviews.api.oauthToken
           )
    val reviewsRepository = ReviewsRepository(database)
    
    // Инициализация RAG сервисов (если Koog включен) - ДО создания ReviewsTools
    // Используем локальный Ollama для генерации эмбеддингов
    val ollamaClient = if (config.koog.enabled) {
        com.prike.data.client.OllamaClient(config.ollama)
    } else {
        null
    }
    
    val embeddingService = if (ollamaClient != null) {
        EmbeddingService(ollamaClient)
    } else {
        null
    }
    if (embeddingService != null) {
        logger.info("EmbeddingService initialized (using local Ollama at ${config.ollama.baseUrl}, model: ${config.ollama.model})")
    }
    
    val reviewSummaryRagService = if (embeddingService != null) {
        ReviewSummaryRagService(
            embeddingService = embeddingService,
            reviewsRepository = reviewsRepository,
            database = database
        )
    } else {
        null
    }
    if (reviewSummaryRagService != null) {
        logger.info("ReviewSummaryRagService initialized (using local Ollama for embeddings)")
    }
    
    // Создаем временный ReviewsTools без koogAgentService для инициализации KoogAgentService
    val reviewsToolsTemp = com.prike.domain.tools.ReviewsTools(
        apiClient = reviewsApiClient,
        repository = reviewsRepository,
        reviewsConfig = config.reviews,
        telegramMCPClient = if (config.telegram.mcp.enabled) telegramMCPClient else null,
        telegramConfig = if (config.telegram.mcp.enabled) config.telegram else null,
        reviewSummaryRagService = reviewSummaryRagService ?: throw IllegalStateException("RAG service is not initialized"),
        koogAgentService = null // Временно null
    )
    
    // Инициализация Koog Agent Service с инструментами
    val koogAgentService = KoogAgentService(config.koog, reviewsToolsTemp)
    
    // Теперь создаем финальный ReviewsTools с koogAgentService
    val reviewsTools = com.prike.domain.tools.ReviewsTools(
        apiClient = reviewsApiClient,
        repository = reviewsRepository,
        reviewsConfig = config.reviews,
        telegramMCPClient = if (config.telegram.mcp.enabled) telegramMCPClient else null,
        telegramConfig = if (config.telegram.mcp.enabled) config.telegram else null,
        reviewSummaryRagService = reviewSummaryRagService ?: throw IllegalStateException("RAG service is not initialized"),
        koogAgentService = koogAgentService
    )
    val koogAgent = koogAgentService.createAgent()
    logger.info("Koog AIAgent initialized")
    
    val reviewsAnalyzerAgent = ReviewsAnalyzerAgent(
        koogAgent = koogAgent,
        reviewsConfig = config.reviews,
        apiClient = reviewsApiClient,
        repository = reviewsRepository
    )
    val reviewsAnalysisService = ReviewsAnalysisService(reviewsAnalyzerAgent, reviewsTools)
    val reviewsController = ReviewsController(reviewsAnalysisService, reviewsTools)
    
    // Инициализация UserProfileService для персонализации
    val userProfileRepository = com.prike.data.repository.ConfigUserProfileRepository(config)
    val userProfileService = com.prike.domain.service.UserProfileService(userProfileRepository)
    
    // Инициализация ResponseFormatter для форматирования ответов
    val responseFormatter = com.prike.domain.service.ResponseFormatter(userProfileService)
    
    // Инициализация InteractionHistoryRepository для сохранения истории взаимодействий
    val interactionHistoryRepository = com.prike.data.repository.SQLiteInteractionHistoryRepository(database)
    
    // Инициализация ChatRepository и ChatService
    val chatRepository = ChatRepository(database)
    val reviewsChatService = ReviewsChatService(chatRepository, koogAgentService, reviewSummaryRagService, userProfileService, responseFormatter, interactionHistoryRepository)
    
    // Инициализация сервисов голосового ввода (если включены)
    val speechRecognitionService = if (config.godAgent.voice.enabled) {
        val modelPath = if (File(config.godAgent.voice.voskModelPath).isAbsolute) {
            config.godAgent.voice.voskModelPath
        } else {
            File(lessonRoot, config.godAgent.voice.voskModelPath).absolutePath
        }
        try {
            SpeechRecognitionService(modelPath).also {
                logger.info("Speech recognition service initialized with model: $modelPath")
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize speech recognition service: ${e.message}", e)
            null
        }
    } else {
        null
    }
    
    val audioConversionService = if (config.godAgent.voice.enabled) {
        AudioConversionService().also {
            logger.info("Audio conversion service initialized")
        }
    } else {
        null
    }
    
    val chatController = ChatController(
        reviewsChatService, 
        chatRepository,
        speechRecognitionService,
        audioConversionService
    )
    
    // Инициализация ProfileController
    val profileController = ProfileController(userProfileService)
    
    // Инициализация MCP сервисов (если включены)
    val mcpConfigService = if (config.godAgent.enabled) {
        val service = com.prike.domain.service.MCPConfigService()
        // Инициализируем конфигурацию с lessonRoot
        service.loadConfig(lessonRoot)
        service.also {
            logger.info("MCPConfigService initialized")
        }
    } else {
        null
    }
    
    val mcpRouterService = if (mcpConfigService != null && embeddingService != null) {
        // Инициализируем MCP клиенты из конфигурации
        val mcpClientsMap = mutableMapOf<String, com.prike.data.client.MCPClientInterface>()
        
        // Получаем список включенных серверов из конфигурации MCP
        val enabledServers = mcpConfigService.getEnabledServers()
        logger.info("Found ${enabledServers.size} enabled MCP servers: ${enabledServers.map { it.name }.joinToString()}")
        
        // Добавляем Telegram MCP клиент, если он включен в конфигурации MCP серверов
        val telegramServerConfig = enabledServers.find { it.name == "Telegram MCP" }
        if (telegramServerConfig != null && telegramServerConfig.enabled) {
            val telegramAdapter = com.prike.data.client.TelegramMCPClientAdapter(telegramMCPClient, "Telegram MCP")
            mcpClientsMap["Telegram MCP"] = telegramAdapter
            logger.info("Telegram MCP client added to router")
            
            // Подключаем Telegram клиент, если он еще не подключен
            if (!telegramMCPClient.isConnected() && config.telegram.mcp.enabled) {
                kotlinx.coroutines.runBlocking {
                    try {
                        telegramMCPClient.connect()
                        logger.info("Telegram MCP client connected")
                    } catch (e: Exception) {
                        logger.warn("Failed to connect Telegram MCP client: ${e.message}")
                    }
                }
            }
        } else {
            logger.debug("Telegram MCP not enabled in MCP servers config")
        }
        
        // Для остальных серверов создаем заглушки, которые будут показывать, что сервер не реализован
        enabledServers.forEach { serverConfig ->
            if (!mcpClientsMap.containsKey(serverConfig.name)) {
                // Создаем заглушку для нереализованных серверов
                val serverName = serverConfig.name
                // Создаем заглушку для нереализованных серверов
                // Возвращаем хотя бы один инструмент-заглушку, чтобы показать, что сервер существует
                val stubClient = object : com.prike.data.client.MCPClientInterface {
                    override suspend fun connect() {
                        logger.warn("Stub client for $serverName - connect() not implemented")
                    }
                    
                    override suspend fun disconnect() {
                        logger.warn("Stub client for $serverName - disconnect() not implemented")
                    }
                    
                    override fun isConnected(): Boolean = false
                    
                    override suspend fun listTools(): List<com.prike.domain.model.MCPTool> {
                        // Возвращаем заглушку-инструмент, чтобы показать, что сервер существует, но не реализован
                        logger.debug("Stub client for $serverName - returning placeholder tool")
                        return listOf(
                            com.prike.domain.model.MCPTool(
                                serverName = serverName,
                                name = "not_implemented",
                                description = "Этот MCP сервер еще не реализован. Сервер существует в конфигурации, но функциональность пока не доступна.",
                                parameters = emptyMap()
                            )
                        )
                    }
                    
                    override suspend fun callTool(toolName: String, arguments: Map<String, Any>): com.prike.domain.model.MCPToolResult {
                        return com.prike.domain.model.MCPToolResult.failure("MCP server '$serverName' is not implemented yet")
                    }
                }
                mcpClientsMap[serverConfig.name] = stubClient
                logger.debug("Stub client created for ${serverConfig.name}")
            }
        }
        
        com.prike.domain.service.MCPRouterService(mcpConfigService, mcpClientsMap).also {
            logger.info("MCPRouterService initialized with ${mcpClientsMap.size} clients")
        }
    } else {
        null
    }
    
    // Инициализация KnowledgeBaseService
    val knowledgeBaseService = if (embeddingService != null) {
        com.prike.domain.service.KnowledgeBaseService(embeddingService, database).also {
            logger.info("KnowledgeBaseService initialized")
        }
    } else {
        null
    }
    
    // Инициализация контроллеров для God Agent
    val mcpServersController = if (mcpConfigService != null && mcpRouterService != null) {
        com.prike.presentation.controller.MCPServersController(mcpConfigService, mcpRouterService).also {
            logger.info("MCPServersController initialized")
        }
    } else {
        null
    }
    
    val knowledgeBaseController = if (knowledgeBaseService != null) {
        com.prike.presentation.controller.KnowledgeBaseController(knowledgeBaseService, lessonRoot).also {
            logger.info("KnowledgeBaseController initialized")
        }
    } else {
        null
    }
    
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
                call.respond(HttpStatusCode.OK, HealthResponse("ok", "lesson-32-god-agent-server"))
            }
            
            // API маршруты для работы с отзывами
            reviewsController.registerRoutes(this)
            
            // API маршруты для чата
            chatController.registerRoutes(this)
            
            // API маршруты для профиля
            profileController.registerRoutes(this)
            
            // API маршруты для MCP серверов
            mcpServersController?.registerRoutes(application)
            
            // API маршруты для базы знаний
            knowledgeBaseController?.registerRoutes(application)
        }
    }.start(wait = true)
}

@Serializable
data class HealthResponse(val status: String, val service: String)

/**
 * Находит корень урока (lesson-32-god-agent)
 */
private fun findLessonRoot(): File {
    var currentDir = File(System.getProperty("user.dir"))
    
    if (currentDir.name == "server") {
        currentDir = currentDir.parentFile
    }
    
    // Ищем lesson-32-god-agent вверх по дереву
    var searchDir: File? = currentDir
    while (searchDir != null) {
        if (searchDir.name == "lesson-32-god-agent") {
            return searchDir
        }
        
        val lessonDir = File(searchDir, "lesson-32-god-agent")
        if (lessonDir.exists()) {
            return lessonDir
        }
        
        searchDir = searchDir.parentFile
    }
    
    // Если не нашли, возвращаем текущую директорию
    return currentDir
}

