package com.prike.presentation.controller

import com.prike.domain.service.RagMCPService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.prike.presentation.dto.ErrorResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Контроллер для API индексации документов
 * Использует RAG MCP сервер для индексации
 */
class IndexingController(
    private val ragMCPService: RagMCPService?,
    private val lessonRoot: File,
    private val projectDocsPath: String? = null,
    private val projectReadmePath: String? = null,
    private val supportDocsPath: String? = null
) {
    private val logger = LoggerFactory.getLogger(IndexingController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Индексировать директорию
            post("/api/indexing/index-directory") {
                try {
                    if (ragMCPService == null) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("RAG MCP service is not available")
                        )
                        return@post
                    }
                    
                    val request = call.receive<IndexDirectoryRequest>()
                    
                    // Валидация входных данных
                    if (request.directoryPath.isBlank()) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("directoryPath cannot be blank")
                        )
                        return@post
                    }
                    
                    // Вызываем MCP инструмент rag_index_documents
                    val arguments = buildJsonObject {
                        put("documentsPath", JsonPrimitive(request.directoryPath))
                    }
                    
                    val result = ragMCPService.callTool("rag_index_documents", arguments)
                    
                    call.respond(IndexDirectoryResponse(
                        success = true,
                        message = result
                    ))
                } catch (e: Exception) {
                    logger.error("Directory indexing error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to index directory: ${e.message}")
                    )
                }
            }
            
            // Индексировать документацию проекта
            post("/api/indexing/index-project-docs") {
                try {
                    if (ragMCPService == null) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("RAG MCP service is not available")
                        )
                        return@post
                    }
                    
                    // Вызываем MCP инструмент rag_index_project_docs
                    val arguments = buildJsonObject {
                        if (projectDocsPath != null) {
                            put("projectDocsPath", JsonPrimitive(projectDocsPath))
                        }
                        if (projectReadmePath != null) {
                            put("projectReadmePath", JsonPrimitive(projectReadmePath))
                        }
                    }
                    
                    val result = ragMCPService.callTool("rag_index_project_docs", arguments)
                    
                    call.respond(IndexProjectDocsResponse(
                        success = true,
                        message = result
                    ))
                } catch (e: Exception) {
                    logger.error("Project docs indexing error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to index project documentation: ${e.message}")
                    )
                }
            }
            
            // Индексировать документацию поддержки
            post("/api/indexing/index-support-docs") {
                try {
                    if (ragMCPService == null) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("RAG MCP service is not available")
                        )
                        return@post
                    }
                    
                    // Вызываем MCP инструмент rag_index_support_docs
                    val arguments = buildJsonObject {
                        if (supportDocsPath != null) {
                            put("supportDocsPath", JsonPrimitive(supportDocsPath))
                        }
                    }
                    
                    val result = ragMCPService.callTool("rag_index_support_docs", arguments)
                    
                    call.respond(IndexSupportDocsResponse(
                        success = true,
                        message = result
                    ))
                } catch (e: Exception) {
                    logger.error("Support docs indexing error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to index support documentation: ${e.message}")
                    )
                }
            }
            
            // Получить статистику индексации
            get("/api/indexing/status") {
                try {
                    if (ragMCPService == null) {
                        logger.warn("RAG MCP service is not available")
                        call.respond(IndexingStatusResponse(
                            documentsCount = 0,
                            chunksCount = 0
                        ))
                        return@get
                    }
                    
                    // Вызываем MCP инструмент rag_get_statistics
                    val result = try {
                        ragMCPService.callTool("rag_get_statistics", kotlinx.serialization.json.buildJsonObject {})
                    } catch (e: IllegalStateException) {
                        if (e.message?.contains("Tool rag_get_statistics not found") == true) {
                            logger.warn("Tool rag_get_statistics not found in RAG MCP server. Returning empty statistics.")
                            call.respond(IndexingStatusResponse(
                                documentsCount = 0,
                                chunksCount = 0
                            ))
                            return@get
                        } else {
                            throw e
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to call rag_get_statistics tool: ${e.message}", e)
                        call.respond(IndexingStatusResponse(
                            documentsCount = 0,
                            chunksCount = 0
                        ))
                        return@get
                    }
                    
                    // Проверяем, не является ли результат ошибкой
                    if (result.startsWith("Tool ") && result.contains("not found")) {
                        logger.warn("Tool rag_get_statistics not found in RAG MCP server. Returning empty statistics.")
                        call.respond(IndexingStatusResponse(
                            documentsCount = 0,
                            chunksCount = 0
                        ))
                        return@get
                    }
                    
                    // Проверяем на ошибки SQLite или другие ошибки
                    if (result.startsWith("Error ") || result.contains("SQLITE_ERROR") || result.contains("no such table")) {
                        logger.warn("RAG MCP server returned an error: $result. Database may not be initialized. Returning empty statistics.")
                        call.respond(IndexingStatusResponse(
                            documentsCount = 0,
                            chunksCount = 0
                        ))
                        return@get
                    }
                    
                    // Парсим результат только если это валидный JSON
                    try {
                        // Проверяем, что результат начинается с { или [
                        if (!result.trimStart().startsWith("{") && !result.trimStart().startsWith("[")) {
                            logger.warn("RAG MCP server returned non-JSON response: ${result.take(100)}")
                            call.respond(IndexingStatusResponse(
                                documentsCount = 0,
                                chunksCount = 0
                            ))
                            return@get
                        }
                        
                        val json = Json.parseToJsonElement(result)
                        if (json is JsonObject) {
                            val documentsCount = json["documentsCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                            val chunksCount = json["chunksCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                            
                            call.respond(IndexingStatusResponse(
                                documentsCount = documentsCount,
                                chunksCount = chunksCount
                            ))
                        } else {
                            logger.warn("Invalid response format from RAG MCP: expected JSON object, got: ${json::class.simpleName}")
                            call.respond(IndexingStatusResponse(
                                documentsCount = 0,
                                chunksCount = 0
                            ))
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to parse statistics response: ${result.take(200)}", e)
                        // Возвращаем пустую статистику вместо ошибки
                        call.respond(IndexingStatusResponse(
                            documentsCount = 0,
                            chunksCount = 0
                        ))
                    }
                } catch (e: Exception) {
                    logger.error("Failed to get indexing status", e)
                    // Вместо 500 ошибки возвращаем пустую статистику
                    call.respond(IndexingStatusResponse(
                        documentsCount = 0,
                        chunksCount = 0
                    ))
                }
            }
            
            // Получить список доступных инструментов (для отладки)
            get("/api/indexing/tools") {
                try {
                    if (ragMCPService == null) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("RAG MCP service is not available")
                        )
                        return@get
                    }
                    
                    val tools = ragMCPService.listTools()
                    call.respond(mapOf(
                        "tools" to tools.map { tool ->
                            mapOf(
                                "name" to tool.name,
                                "description" to tool.description
                            )
                        }
                    ))
                } catch (e: Exception) {
                    logger.error("Failed to list tools", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to list tools: ${e.message}")
                    )
                }
            }
            
            // Получить список документов
            get("/api/indexing/documents") {
                try {
                    if (ragMCPService == null) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("RAG MCP service is not available")
                        )
                        return@get
                    }
                    
                    // Вызываем MCP инструмент rag_get_documents
                    val result = ragMCPService.callTool("rag_get_documents", buildJsonObject {})
                    
                    // Проверяем, не является ли результат ошибкой
                    if (result.startsWith("Tool ") && result.contains("not found")) {
                        logger.warn("Tool rag_get_documents not found in RAG MCP server. Returning empty list.")
                        call.respond(DocumentsListResponse(documents = emptyList()))
                        return@get
                    }
                    
                    // Парсим результат
                    try {
                        val json = Json.parseToJsonElement(result)
                        if (json is JsonObject) {
                            val documentsArray = json["documents"]?.jsonArray ?: JsonArray(emptyList())
                            val documents = documentsArray.mapNotNull { docJson ->
                                if (docJson is JsonObject) {
                                    val rawFilePath = docJson["filePath"]?.jsonPrimitive?.content ?: ""
                                    // Нормализуем путь - убираем полный путь, оставляем только относительный
                                    val normalizedPath = normalizeDocumentPath(rawFilePath, lessonRoot)
                                    
                                    DocumentInfo(
                                        id = docJson["id"]?.jsonPrimitive?.content ?: "",
                                        filePath = normalizedPath,
                                        title = docJson["title"]?.jsonPrimitive?.contentOrNull,
                                        indexedAt = docJson["indexedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                                        chunkCount = docJson["chunkCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                                    )
                                } else {
                                    null
                                }
                            }
                            
                            call.respond(DocumentsListResponse(documents = documents))
                        } else {
                            throw IllegalArgumentException("Invalid response format from RAG MCP: expected JSON object")
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to parse documents response: $result", e)
                        // Возвращаем пустой список вместо ошибки
                        call.respond(DocumentsListResponse(documents = emptyList()))
                    }
                } catch (e: Exception) {
                    logger.error("Failed to get documents list", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get documents list: ${e.message}")
                    )
                }
            }
        }
    }
}

@Serializable
data class IndexDirectoryRequest(
    val directoryPath: String
)

@Serializable
data class IndexDirectoryResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class IndexProjectDocsResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class IndexSupportDocsResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class IndexingStatusResponse(
    val documentsCount: Int,
    val chunksCount: Int
)

@Serializable
data class DocumentsListResponse(
    val documents: List<DocumentInfo>
)

@Serializable
data class DocumentInfo(
    val id: String,
    val filePath: String,
    val title: String?,
    val indexedAt: Long,
    val chunkCount: Int
)

/**
 * Нормализует путь к документу - убирает полный путь, оставляет только относительный
 */
private fun normalizeDocumentPath(path: String, lessonRoot: File): String {
    // Если путь абсолютный и содержит lessonRoot, извлекаем относительный путь
    if (path.startsWith(lessonRoot.absolutePath)) {
        val relativePath = path.removePrefix(lessonRoot.absolutePath).trimStart('/')
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
