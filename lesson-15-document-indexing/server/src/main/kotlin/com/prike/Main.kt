package com.prike

import com.prike.config.Config
import com.prike.presentation.controller.ClientController
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.routing.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
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
    
    // Регистрация контроллеров
    val clientDir = File(lessonRoot, "client")
    val clientController = ClientController(clientDir)
    
    routing {
        // Статические файлы для UI
        clientController.registerRoutes(this)
        
        // Health check API
        get("/api/health") {
            call.respond(mapOf(
                "status" to "ok",
                "service" to "lesson-15-document-indexing-server"
            ))
        }
    }
    
    logger.info("Server started on ${config.server.host}:${config.server.port}")
}

/**
 * Находит корень урока (lesson-15-document-indexing)
 */
private fun findLessonRoot(): File {
    var currentDir = File(System.getProperty("user.dir"))
    
    if (currentDir.name == "server") {
        currentDir = currentDir.parentFile
    }
    
    // Ищем lesson-15-document-indexing вверх по дереву
    var searchDir = currentDir
    while (searchDir != null && searchDir.parentFile != null) {
        if (searchDir.name == "lesson-15-document-indexing") {
            return searchDir
        }
        
        val lessonDir = File(searchDir, "lesson-15-document-indexing")
        if (lessonDir.exists()) {
            return lessonDir
        }
        
        searchDir = searchDir.parentFile
    }
    
    // Если не нашли, возвращаем текущую директорию
    return currentDir
}

