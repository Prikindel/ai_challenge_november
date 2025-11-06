package com.prike.presentation.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

/**
 * Контроллер для отдачи статических файлов из папки client
 */
class ClientController(private val clientDir: File) {
    
    fun configureRoutes(routing: Routing) {
        // Главная страница - отдаем index.html
        routing.get("/") {
            call.serveStaticFile("index.html")
        }
        
        // Статические файлы (style.css, app.js и т.д.)
        routing.get("/{filename}") {
            val filename = call.parameters["filename"] ?: return@get call.respond(HttpStatusCode.NotFound)
            
            if (filename.endsWith(".css") || filename.endsWith(".js") || filename == "index.html") {
                call.serveStaticFile(filename)
            } else {
                call.respond(HttpStatusCode.NotFound, "File not found")
            }
        }
    }
    
    private suspend fun ApplicationCall.serveStaticFile(filename: String) {
        val file = File(clientDir, filename)
        
        if (file.exists() && file.isFile) {
            when {
                filename.endsWith(".html") -> respondText(file.readText(), ContentType.Text.Html)
                filename.endsWith(".css") -> respondText(file.readText(), ContentType.Text.CSS)
                filename.endsWith(".js") -> respondText(file.readText(), ContentType.Application.JavaScript)
                else -> respondFile(file)
            }
        } else {
            respond(HttpStatusCode.NotFound, "File not found: $filename")
        }
    }
}

