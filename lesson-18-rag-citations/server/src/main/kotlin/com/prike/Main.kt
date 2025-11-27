package com.prike

import com.prike.config.Config
import com.prike.data.client.OllamaClient
import com.prike.data.repository.KnowledgeBaseRepository
import com.prike.domain.indexing.CosineSimilarityCalculator
import com.prike.domain.indexing.TextChunker
import com.prike.domain.indexing.VectorNormalizer
import com.prike.domain.service.DocumentIndexer
import com.prike.domain.service.DocumentLoader
import com.prike.domain.service.EmbeddingService
import com.prike.domain.service.KnowledgeBaseSearchService
import com.prike.domain.service.LLMService
import com.prike.domain.service.PromptBuilder
import com.prike.domain.service.RAGService
import com.prike.domain.service.ComparisonService
import com.prike.presentation.controller.ClientController
import com.prike.presentation.controller.IndexingController
import com.prike.presentation.controller.SearchController
import com.prike.presentation.controller.LLMController
import com.prike.presentation.controller.RAGController
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.callloging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import kotlinx.coroutines.runBlocking
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
    
    // Находим корень урока
    val lessonRoot = findLessonRoot()
    logger.info("Lesson root: ${lessonRoot.absolutePath}")
    
    // Инициализация компонентов
    
    // 1. Ollama клиент
    val ollamaClient = OllamaClient(config.ollama)
    
    // 2. Сервис эмбеддингов
    val embeddingService = EmbeddingService(ollamaClient)
    
    // 3. Нормализатор векторов
    val vectorNormalizer = VectorNormalizer()
    
    // 4. База знаний
    val dbPath = File(lessonRoot, config.knowledgeBase.databasePath).absolutePath
    val knowledgeBaseRepository = KnowledgeBaseRepository(dbPath)
    
    // 5. Разбивка на чанки
    val textChunker = TextChunker(
        chunkSize = config.indexing.chunkSize,
        overlapSize = config.indexing.overlapSize
    )
    
    // 6. Загрузчик документов (с базовым путём относительно корня урока)
    val documentLoader = DocumentLoader(lessonRoot)
    
    // 7. Индексатор документов
    val documentIndexer = DocumentIndexer(
        documentLoader = documentLoader,
        textChunker = textChunker,
        embeddingService = embeddingService,
        vectorNormalizer = vectorNormalizer,
        knowledgeBaseRepository = knowledgeBaseRepository
    )
    
    // 8. Калькулятор косинусного сходства
    val similarityCalculator = CosineSimilarityCalculator()
    
    // 9. Сервис поиска
    val searchService = KnowledgeBaseSearchService(
        embeddingService = embeddingService,
        vectorNormalizer = vectorNormalizer,
        knowledgeBaseRepository = knowledgeBaseRepository,
        similarityCalculator = similarityCalculator
    )
    
    // 10. LLM сервис (OpenRouter)
    val llmService = LLMService(
        aiConfig = config.ai,
        defaultTemperature = config.ai.temperature,
        defaultMaxTokens = config.ai.maxTokens
    )
    
    // 11. PromptBuilder для формирования промптов с контекстом
    val promptBuilder = PromptBuilder()
    
    // 12. Конфигурация фильтрации (если включена)
    val filterConfig = if (config.rag.filter.enabled) {
        config.rag.filter
    } else {
        null
    }
    
    // 13. RAG сервис
    val ragService = RAGService(
        searchService = searchService,
        llmService = llmService,
        promptBuilder = promptBuilder,
        filterConfig = filterConfig,
        aiConfig = config.ai
    )
    
    // 14. Comparison сервис
    val comparisonService = ComparisonService(
        ragService = ragService,
        llmService = llmService
    )
    
    // Регистрация контроллеров
    val clientDir = File(lessonRoot, "client")
    val clientController = ClientController(clientDir)
    val indexingController = IndexingController(documentIndexer, knowledgeBaseRepository)
    val searchController = SearchController(searchService, knowledgeBaseRepository)
    val llmController = LLMController(llmService)
    val ragController = RAGController(ragService, llmService, comparisonService, filterConfig)
    val documentController = com.prike.presentation.controller.DocumentController(knowledgeBaseRepository)
    
    routing {
        // Статические файлы для UI
        clientController.registerRoutes(this)
        
        // Health check API
        get("/api/health") {
            val ollamaHealthy = runBlocking {
                ollamaClient.checkHealth()
            }
            call.respond(mapOf(
                "status" to "ok",
                "service" to "lesson-18-rag-citations-server",
                "ollama" to ollamaHealthy
            ))
        }
        
        // API маршруты
        indexingController.registerRoutes(this)
        searchController.registerRoutes(this)
        llmController.registerRoutes(this)
        ragController.registerRoutes(this)
        documentController.registerRoutes(this)
    }
    
    // Закрытие ресурсов при остановке
    environment.monitor.subscribe(ApplicationStopped) {
        ollamaClient.close()
        llmService.close()
        ragService.close()
    }
    
    logger.info("Server started on ${config.server.host}:${config.server.port}")
}

/**
 * Находит корень урока (lesson-18-rag-citations)
 */
private fun findLessonRoot(): File {
    var currentDir = File(System.getProperty("user.dir"))
    
    if (currentDir.name == "server") {
        currentDir = currentDir.parentFile
    }
    
    // Ищем lesson-18-rag-citations вверх по дереву
    var searchDir: File? = currentDir
    while (searchDir != null) {
        if (searchDir.name == "lesson-18-rag-citations") {
            return searchDir
        }
        
        val lessonDir = File(searchDir, "lesson-18-rag-citations")
        if (lessonDir.exists()) {
            return lessonDir
        }
        
        searchDir = searchDir.parentFile
    }
    
    // Если не нашли, возвращаем текущую директорию
    return currentDir
}

