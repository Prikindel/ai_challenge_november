package com.prike

import com.prike.config.Config
import com.prike.presentation.controller.ClientController
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
import org.slf4j.LoggerFactory
import java.io.File

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("Main")
    
    val config = Config.load()
    val lessonRoot = findLessonRoot()
    
    logger.info("Starting lesson-24-real-task server...")
    logger.info("Lesson root: ${lessonRoot.absolutePath}")
    
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
                call.respond(HttpStatusCode.OK, HealthResponse("ok", "lesson-24-real-task-server"))
            }
            
            // API маршруты будут добавлены в следующих коммитах
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

