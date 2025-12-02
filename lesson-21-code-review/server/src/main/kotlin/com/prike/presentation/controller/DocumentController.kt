package com.prike.presentation.controller

import com.prike.domain.service.GitMCPService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Контроллер для получения документов
 */
class DocumentController(
    private val gitMCPService: GitMCPService?,
    private val lessonRoot: File
) {
    private val logger = LoggerFactory.getLogger(DocumentController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Получить содержимое документа по пути
            get("/api/documents/{path...}") {
                try {
                    val pathSegments = call.parameters.getAll("path") ?: emptyList()
                    val documentPath = pathSegments.joinToString("/")
                    
                    if (documentPath.isBlank()) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("Document path is required")
                        )
                        return@get
                    }
                    
                    logger.debug("Requesting document: $documentPath")
                    
                    // Нормализуем путь - убираем полный путь, оставляем только относительный
                    val normalizedPath = normalizeDocumentPath(documentPath, lessonRoot)
                    
                    // Пробуем прочитать файл через MCP инструмент read_file
                    val fileContent = if (gitMCPService != null && gitMCPService.isConnected()) {
                        try {
                            val arguments = kotlinx.serialization.json.buildJsonObject {
                                put("path", kotlinx.serialization.json.JsonPrimitive(normalizedPath))
                            }
                            gitMCPService.callTool("read_file", arguments)
                        } catch (e: Exception) {
                            logger.warn("Failed to read file via MCP: ${e.message}, trying direct file access")
                            null
                        }
                    } else {
                        null
                    }
                    
                    // Если MCP не сработал, пробуем прочитать файл напрямую
                    val content = fileContent ?: run {
                        val file = File(lessonRoot, normalizedPath)
                        if (!file.exists() || !file.isFile) {
                            // Пробуем найти файл относительно project/
                            val projectFile = File(lessonRoot, "project/$normalizedPath")
                            if (projectFile.exists() && projectFile.isFile) {
                                projectFile.readText()
                            } else {
                                throw IllegalArgumentException("File not found: $normalizedPath")
                            }
                        } else {
                            file.readText()
                        }
                    }
                    
                    // Определяем тип файла
                    val isMarkdown = documentPath.endsWith(".md", ignoreCase = true)
                    val documentTitle = documentPath.split("/").lastOrNull() ?: documentPath
                    
                    call.respond(
                        DocumentResponse(
                            documentPath = documentPath,
                            documentTitle = documentTitle,
                            content = content,
                            isMarkdown = isMarkdown,
                            indexedAt = System.currentTimeMillis() / 1000, // Текущее время как заглушка
                            chunksCount = 0 // Не знаем количество чанков без запроса к RAG
                        )
                    )
                } catch (e: IllegalArgumentException) {
                    logger.warn("Document not found: ${e.message}")
                    call.respond(
                        io.ktor.http.HttpStatusCode.NotFound,
                        ErrorResponse("Document not found: ${e.message}")
                    )
                } catch (e: Exception) {
                    logger.error("Failed to get document", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get document: ${e.message}")
                    )
                }
            }
        }
    }
    
    /**
     * Нормализует путь к документу - убирает полный путь, оставляет только относительный
     */
    private fun normalizeDocumentPath(path: String, lessonRoot: File): String {
        // Если путь абсолютный и содержит lessonRoot, извлекаем относительный путь
        if (path.startsWith(lessonRoot.absolutePath)) {
            val relativePath = path.removePrefix(lessonRoot.absolutePath).trimStart('/')
            logger.debug("Normalized absolute path: $path -> $relativePath")
            return relativePath
        }
        
        // Если путь начинается с project/, оставляем как есть
        if (path.startsWith("project/")) {
            return path
        }
        
        // Если путь не начинается с project/, но должен быть в project/, добавляем префикс
        // Но только если это не полный путь
        if (!path.contains("/") || path.startsWith("/")) {
            // Это может быть просто имя файла или полный путь
            // Пробуем найти файл в project/
            val projectFile = File(lessonRoot, "project/$path")
            if (projectFile.exists()) {
                return "project/$path"
            }
        }
        
        // Возвращаем путь как есть, если он уже относительный
        return path
    }
}

@Serializable
data class DocumentResponse(
    val documentPath: String,
    val documentTitle: String,
    val content: String,
    val isMarkdown: Boolean = false,
    val indexedAt: Long = 0,
    val chunksCount: Int = 0
)

@Serializable
data class ErrorResponse(
    val message: String
)

