package com.prike

import com.prike.config.Config
import com.prike.data.DatabaseManager
import com.prike.data.repository.DataRepository
import com.prike.domain.service.LLMService
import com.prike.domain.service.PromptTemplateService
import com.prike.presentation.controller.ClientController
import com.prike.presentation.controller.DataController
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("Main")
    
    val config = Config.load()
    val lessonRoot = findLessonRoot()
    
    logger.info("Starting lesson-29-local-analyst server...")
    logger.info("Lesson root: ${lessonRoot.absolutePath}")
    
    // Проверяем, что локальная LLM включена
    if (config.localLLM == null || !config.localLLM.enabled) {
        logger.error("Local LLM is not enabled in configuration!")
        logger.error("Please set localLLM.enabled: true in config/server.yaml")
        return
    }
    
    // Инициализация БД
    val databaseManager = DatabaseManager(config.database, lessonRoot)
    databaseManager.init()
    val database = databaseManager.database
    
    // Инициализация репозитория данных
    val dataRepository = DataRepository(database)
    
    // Инициализация сервисов
    val promptTemplateService = PromptTemplateService()
    val llmService = LLMService(
        localLLMConfig = config.localLLM,
        promptTemplateService = promptTemplateService
    )
    
    // Инициализация контроллеров
    val dataController = DataController(dataRepository)
    
    // Статический контент для UI
    val clientDir = File(lessonRoot, "client")
    val clientController = ClientController(clientDir)
    
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
                call.respond(HttpStatusCode.OK, HealthResponse("ok", "lesson-29-local-analyst-server"))
            }
            
            // API маршруты для работы с данными
            dataController.registerRoutes(this)
        }
    }.start(wait = true)
}

@Serializable
data class HealthResponse(val status: String, val service: String)

/**
 * Находит корень урока (lesson-29-local-analyst)
 */
private fun findLessonRoot(): File {
    var currentDir = File(System.getProperty("user.dir"))
    
    if (currentDir.name == "server") {
        currentDir = currentDir.parentFile
    }
    
    // Ищем lesson-29-local-analyst вверх по дереву
    var searchDir: File? = currentDir
    while (searchDir != null) {
        if (searchDir.name == "lesson-29-local-analyst") {
            return searchDir
        }
        
        val lessonDir = File(searchDir, "lesson-29-local-analyst")
        if (lessonDir.exists()) {
            return lessonDir
        }
        
        searchDir = searchDir.parentFile
    }
    
    // Если не нашли, возвращаем текущую директорию
    return currentDir
}
