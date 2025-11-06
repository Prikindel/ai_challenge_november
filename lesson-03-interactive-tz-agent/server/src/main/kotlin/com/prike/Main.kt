package com.prike

import com.prike.di.AppModule
import com.prike.presentation.controller.ChatController
import com.prike.presentation.controller.ClientController
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.callloging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

/**
 * Точка входа приложения
 */
fun main() {
    embeddedServer(Netty, environment = applicationEngineEnvironment {
        module {
            module()
        }
        connector {
            port = Config.serverPort
            host = Config.serverHost
        }
    }).start(wait = true)
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
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        })
    }

    install(CallLogging) {
        level = Level.INFO
    }

    val clientController = ClientController(AppModule.getClientDirectory())
    
    // Создаем агента для сбора ТЗ
    val tzAgent = AppModule.createTZAgent()
    val chatController = tzAgent?.let { ChatController(it) }

    routing {
        // Сначала регистрируем статические файлы (client)
        clientController.configureRoutes(this)
        
        // Затем регистрируем контроллер чата, если агент создан
        chatController?.configureRoutes(this) ?: run {
            val logger = LoggerFactory.getLogger("Application")
            logger.warn("TZAgent не создан. Проверьте конфигурацию AI (OPENAI_API_KEY в .env)")
        }
    }

    environment.monitor.subscribe(ApplicationStopped) {
        AppModule.close()
    }
}

