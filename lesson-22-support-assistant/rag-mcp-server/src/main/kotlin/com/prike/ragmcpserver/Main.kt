package com.prike.ragmcpserver

import com.prike.ragmcpserver.config.RagMCPConfig
import com.prike.ragmcpserver.data.client.OllamaClient
import com.prike.ragmcpserver.data.repository.KnowledgeBaseRepository
import com.prike.ragmcpserver.domain.indexing.CosineSimilarityCalculator
import com.prike.ragmcpserver.domain.indexing.TextChunker
import com.prike.ragmcpserver.domain.indexing.VectorNormalizer
import com.prike.ragmcpserver.domain.service.DocumentIndexer
import com.prike.ragmcpserver.domain.service.DocumentLoader
import com.prike.ragmcpserver.domain.service.EmbeddingService
import com.prike.ragmcpserver.domain.service.KnowledgeBaseSearchService
import com.prike.ragmcpserver.server.RagMCPServer
import com.prike.ragmcpserver.tools.ToolRegistry
import com.prike.ragmcpserver.tools.handlers.InternalRagServiceProvider
import com.prike.mcpcommon.server.BaseMCPServer
import io.modelcontextprotocol.kotlin.sdk.Implementation
import org.slf4j.LoggerFactory
import java.io.File

fun main() {
    val logger = LoggerFactory.getLogger("RagMCPServerMain")
    
    try {
        logger.info("Starting RAG MCP Server...")
        
        // Загружаем конфигурацию из переменных окружения
        val config = RagMCPConfig.fromEnvironment()
        logger.info("Configuration loaded: ollama=${config.ollama.baseUrl}, db=${config.knowledgeBase.databasePath}")
        
        // Находим корень проекта (lesson-22-support-assistant)
        val lessonRoot = findLessonRoot()
        logger.info("Lesson root: ${lessonRoot.absolutePath}")
        
        // Инициализация компонентов RAG
        
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
        
        // 6. Загрузчик документов
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
        
        // 10. Внутренний провайдер RAG сервисов
        val ragServiceProvider = InternalRagServiceProvider(searchService)
        
        // 11. Создание реестра инструментов
        val toolRegistry = ToolRegistry(
            ragServiceProvider = ragServiceProvider,
            documentIndexer = documentIndexer,
            knowledgeBaseRepository = knowledgeBaseRepository,
            lessonRoot = lessonRoot,
            config = config
        )
        
        // 12. Создание и запуск MCP сервера
        val server = RagMCPServer(
            serverInfo = Implementation(
                name = "rag-mcp-server",
                version = "1.0.0"
            ),
            toolRegistry = toolRegistry
        )
        
        logger.info("RAG MCP Server initialized successfully")
        server.start()
        
        // Ожидание завершения (сервер работает в stdio режиме)
        Thread.currentThread().join()
    } catch (e: Exception) {
        logger.error("Failed to start RAG MCP server: ${e.message}", e)
        System.exit(1)
    }
}

/**
 * Находит корень урока (lesson-22-support-assistant)
 */
private fun findLessonRoot(): File {
    var currentDir = File(System.getProperty("user.dir"))
    
    // Если запускаем из rag-mcp-server, поднимаемся на уровень выше
    if (currentDir.name == "rag-mcp-server") {
        currentDir = currentDir.parentFile
    }
    
    // Ищем lesson-22-support-assistant вверх по дереву
    var searchDir: File? = currentDir
    while (searchDir != null) {
        if (searchDir.name == "lesson-22-support-assistant") {
            return searchDir
        }
        
        val lessonDir = File(searchDir, "lesson-22-support-assistant")
        if (lessonDir.exists()) {
            return lessonDir
        }
        
        searchDir = searchDir.parentFile
    }
    
    // Если не нашли, возвращаем текущую директорию
    return currentDir
}
