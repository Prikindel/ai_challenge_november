package com.prike.presentation.controller

import com.prike.data.repository.KnowledgeBaseRepository
import com.prike.presentation.dto.DocumentContentResponse
import com.prike.presentation.dto.ErrorResponse
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Контроллер для API просмотра документов
 */
class DocumentController(
    private val knowledgeBaseRepository: KnowledgeBaseRepository
) {
    private val logger = LoggerFactory.getLogger(DocumentController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Получить содержимое документа по пути
            get("/api/documents/{documentPath...}") {
                try {
                    val pathParts = call.parameters.getAll("documentPath") ?: emptyList()
                    val documentPath = if (pathParts.isEmpty()) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("documentPath is required")
                        )
                        return@get
                    } else {
                        pathParts.joinToString("/")
                    }
                    
                    // Валидация пути - проверка на path traversal атаки
                    if (!isValidDocumentPath(documentPath)) {
                        logger.warn("Invalid document path detected: $documentPath")
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid document path")
                        )
                        return@get
                    }
                    
                    // Получаем документ из базы знаний
                    val document = knowledgeBaseRepository.getDocumentByPath(documentPath)
                    
                    if (document == null) {
                        logger.warn("Document not found: $documentPath")
                        call.respond(
                            io.ktor.http.HttpStatusCode.NotFound,
                            ErrorResponse("Document not found: $documentPath")
                        )
                        return@get
                    }
                    
                    logger.debug("Retrieved document: $documentPath")
                    
                    call.respond(
                        DocumentContentResponse(
                            documentPath = document.filePath,
                            documentTitle = document.title,
                            content = document.content,
                            indexedAt = document.indexedAt,
                            chunksCount = document.chunkCount
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error retrieving document", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to retrieve document: ${e.message}")
                    )
                }
            }
        }
    }
    
    /**
     * Валидирует путь к документу для предотвращения path traversal атак
     * 
     * Проверяет:
     * - Отсутствие "../" в пути
     * - Отсутствие абсолютных путей
     * - Разрешённые символы
     */
    private fun isValidDocumentPath(path: String): Boolean {
        // Проверка на path traversal атаки
        if (path.contains("..") || path.contains("../")) {
            return false
        }
        
        // Проверка на абсолютные пути
        val file = File(path)
        if (file.isAbsolute) {
            return false
        }
        
        // Проверка на наличие небезопасных символов
        val unsafeChars = setOf('<', '>', ':', '"', '|', '?', '*')
        if (path.any { it in unsafeChars && it != ':' && it != '/' }) {
            return false
        }
        
        // Путь не должен быть пустым
        if (path.isBlank()) {
            return false
        }
        
        return true
    }
}

