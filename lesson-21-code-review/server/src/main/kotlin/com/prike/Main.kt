package com.prike

import com.prike.config.Config
import com.prike.data.repository.ChatRepository
import com.prike.domain.service.ChatPromptBuilder
import com.prike.domain.service.ChatService
import com.prike.domain.service.LLMService
import com.prike.domain.service.PromptBuilder
import com.prike.domain.service.RequestRouterService
import com.prike.presentation.controller.ChatController
import com.prike.presentation.controller.ClientController
import com.prike.presentation.controller.DocumentController
import com.prike.presentation.controller.IndexingController
import com.prike.presentation.controller.LLMController
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.http.*
import io.ktor.server.plugins.cors.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("Main")
    
    val config = Config.load()
    val lessonRoot = findLessonRoot()
    
    logger.info("Starting lesson-21-code-review server...")
    logger.info("Lesson root: ${lessonRoot.absolutePath}")
    
    // Инициализация компонентов
    
    // 1. LLM сервис (OpenRouter)
    val llmService = LLMService(
        aiConfig = config.ai,
        defaultTemperature = config.ai.temperature,
        defaultMaxTokens = config.ai.maxTokens
    )
    
    // 2. PromptBuilder для формирования промптов с контекстом
    val promptBuilder = PromptBuilder()
    
    // 3. Chat Repository для истории диалога
    val dbPath = File(lessonRoot, config.knowledgeBase.databasePath).absolutePath
    val chatRepository = ChatRepository(dbPath)
    
    // 4. ChatPromptBuilder для оптимизации истории диалога
    val chatPromptBuilder = ChatPromptBuilder(
        historyConfig = config.chat.history,
        basePromptBuilder = promptBuilder
    )
    
    // 5. Git MCP Service (опционально, если включен в конфигурации)
    val gitMCPService = if (config.git.mcp.enabled) {
        val gitMCPClient = com.prike.data.client.GitMCPClient()
        val service = com.prike.domain.service.GitMCPService(
            gitMCPClient = gitMCPClient,
            lessonRoot = lessonRoot,
            gitMCPJarPath = config.git.mcp.jarPath
        )
        // Подключаемся к Git MCP серверу асинхронно
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try {
                service.connect()
                logger.info("Git MCP service connected successfully")
            } catch (e: Exception) {
                logger.warn("Failed to connect to Git MCP server: ${e.message}. Git branch information will not be available.")
            }
        }
        service
    } else {
        null
    }
    
    // 6. RAG MCP Service (опционально, если включен в конфигурации)
    val ragMCPService = if (config.rag.mcp?.enabled == true) {
        val ragMCPClient = com.prike.data.client.RagMCPClient()
        val service = com.prike.domain.service.RagMCPService(
            ragMCPClient = ragMCPClient,
            lessonRoot = lessonRoot,
            ragMCPJarPath = config.rag.mcp.jarPath
        )
        // Подключаемся к RAG MCP серверу асинхронно
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try {
                service.connect()
                logger.info("RAG MCP service connected successfully")
            } catch (e: Exception) {
                logger.warn("Failed to connect to RAG MCP server: ${e.message}. RAG MCP tools will not be available.")
            }
        }
        service
    } else {
        null
    }
    
    // 7. Request Router Service для динамического роутинга через LLM
    val requestRouter = if (gitMCPService != null || ragMCPService != null) {
        RequestRouterService(
            llmService = llmService,
            gitMCPService = gitMCPService,
            ragMCPService = ragMCPService
        )
    } else {
        null
    }
    
    // 8. Chat Service для обработки сообщений с RAG и историей
    val chatService = ChatService(
        chatRepository = chatRepository,
        chatPromptBuilder = chatPromptBuilder,
        llmService = llmService,
        gitMCPService = gitMCPService,
        ragMCPService = ragMCPService,
        requestRouter = requestRouter
    )
    
    // 9. Code Review Service для автоматического ревью кода
    val codeReviewService = if (gitMCPService != null) {
        com.prike.domain.service.CodeReviewService(
            gitMCPService = gitMCPService,
            ragMCPService = ragMCPService,
            llmService = llmService
        )
    } else {
        null
    }
    
    // Регистрация контроллеров
    val clientDir = File(lessonRoot, "client")
    val clientController = ClientController(clientDir)
    val indexingController = IndexingController(
        ragMCPService = ragMCPService,
        lessonRoot = lessonRoot,
        projectDocsPath = config.indexing.projectDocsPath,
        projectReadmePath = config.indexing.projectReadmePath
    )
    val llmController = LLMController(llmService)
    val chatController = ChatController(chatService, chatRepository, gitMCPService)
    val documentController = DocumentController(
        gitMCPService = gitMCPService,
        lessonRoot = lessonRoot
    )
    val codeReviewController = if (codeReviewService != null) {
        com.prike.presentation.controller.CodeReviewController(codeReviewService)
    } else {
        null
    }
    
    embeddedServer(Netty, port = config.server.port, host = config.server.host) {
        install(ContentNegotiation) {
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
                call.respond(HttpStatusCode.OK, HealthResponse("ok", "lesson-21-code-review-server"))
            }
            
            // API маршруты
            indexingController.registerRoutes(this)
            llmController.registerRoutes(this)
            chatController.registerRoutes(this)
            documentController.registerRoutes(this)
            codeReviewController?.registerRoutes(this)
        }
        
        // Закрытие ресурсов при остановке
        environment.monitor.subscribe(ApplicationStopped) {
            llmService.close()
            runBlocking {
                gitMCPService?.disconnect()
                ragMCPService?.disconnect()
            }
        }
    }.start(wait = true)
}

@Serializable
data class HealthResponse(val status: String, val service: String)

/**
 * Находит корень урока (lesson-21-code-review)
 */
private fun findLessonRoot(): File {
    var currentDir = File(System.getProperty("user.dir"))
    
    if (currentDir.name == "server") {
        currentDir = currentDir.parentFile
    }
    
    // Ищем lesson-21-code-review вверх по дереву
    var searchDir: File? = currentDir
    while (searchDir != null) {
        if (searchDir.name == "lesson-21-code-review") {
            return searchDir
        }
        
        val lessonDir = File(searchDir, "lesson-21-code-review")
        if (lessonDir.exists()) {
            return lessonDir
        }
        
        searchDir = searchDir.parentFile
    }
    
    // Если не нашли, возвращаем текущую директорию
    return currentDir
}
