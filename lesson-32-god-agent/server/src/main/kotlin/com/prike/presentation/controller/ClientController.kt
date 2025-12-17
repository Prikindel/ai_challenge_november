package com.prike.presentation.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

/**
 * Контроллер для статических файлов клиента
 */
class ClientController(private val clientDir: File) {
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Главная страница
            get("/") {
                val indexFile = File(clientDir, "index.html")
                if (indexFile.exists()) {
                    call.respondFile(indexFile)
                } else {
                    call.respondText("Client files not found", status = HttpStatusCode.NotFound)
                }
            }
            
            // Статические файлы (HTML, JS, CSS)
            staticFiles("/", clientDir) {
                default("index.html")
            }
        }
    }
}

