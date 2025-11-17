package com.prike

import com.prike.di.AppModule
import com.prike.presentation.controller.ClientController
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import org.slf4j.event.Level

/**
 * Точка входа приложения
 */
fun main() {
    embeddedServer(Netty, port = Config.serverPort, host = Config.serverHost) {
        module()
    }.start(wait = true)
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

    val clientController = ClientController(AppModule.getClientDirectory())
    val mcpController = AppModule.createMCPController()

    routing {
        clientController.configureRoutes(this)
        mcpController.configureRoutes(this)
    }

    environment.monitor.subscribe(ApplicationStopped) {
        AppModule.close()
    }
}

