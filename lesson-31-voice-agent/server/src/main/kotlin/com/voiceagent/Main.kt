package com.voiceagent

import com.voiceagent.di.AppModule
import com.voiceagent.presentation.controller.ClientController
import com.voiceagent.presentation.controller.VoiceController
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.routing.*
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
        json()
    }

    install(CallLogging) {
        level = Level.INFO
    }

    val chatController = AppModule.createChatController()
    val voiceController = AppModule.createVoiceController()
    val clientController = ClientController(AppModule.getClientDirectory())

    routing {
        chatController.configureRoutes(this)
        voiceController.voiceRoutes(this)
        clientController.configureRoutes(this)
    }

    environment.monitor.subscribe(ApplicationStopped) {
        AppModule.close()
    }
}
