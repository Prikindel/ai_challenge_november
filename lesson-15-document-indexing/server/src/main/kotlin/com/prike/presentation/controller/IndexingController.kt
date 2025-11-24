package com.prike.presentation.controller

import com.prike.domain.service.DocumentIndexer
import com.prike.data.repository.KnowledgeBaseRepository
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.prike.presentation.dto.DocumentInfo
import com.prike.presentation.dto.DocumentsListResponse
import com.prike.presentation.dto.ErrorResponse
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Контроллер для API индексации документов
 */
class IndexingController(
    private val documentIndexer: DocumentIndexer,
    private val knowledgeBaseRepository: KnowledgeBaseRepository
) {
    private val logger = LoggerFactory.getLogger(IndexingController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Индексировать документ
            post("/api/indexing/index") {
                try {
                    val request = call.receive<IndexDocumentRequest>()
                    
                    // Валидация входных данных
                    if (request.filePath.isBlank()) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("filePath cannot be blank")
                        )
                        return@post
                    }
                    
                    val result = documentIndexer.indexDocument(request.filePath)
                    
                    call.respond(IndexDocumentResponse(
                        success = result.success,
                        documentId = result.documentId,
                        chunksCount = result.chunksCount,
                        error = result.error,
                        errorsCount = result.errorsCount
                    ))
                } catch (e: Exception) {
                    logger.error("Indexing error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to index document: ${e.message}")
                    )
                }
            }
            
            // Индексировать директорию
            post("/api/indexing/index-directory") {
                try {
                    val request = call.receive<IndexDirectoryRequest>()
                    
                    // Валидация входных данных
                    if (request.directoryPath.isBlank()) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("directoryPath cannot be blank")
                        )
                        return@post
                    }
                    
                    val results = documentIndexer.indexDirectory(request.directoryPath)
                    
                    val successCount = results.count { it.success }
                    val totalChunks = results.sumOf { it.chunksCount }
                    
                    call.respond(IndexDirectoryResponse(
                        success = true,
                        documentsProcessed = results.size,
                        documentsSucceeded = successCount,
                        totalChunks = totalChunks,
                        results = results.map { result ->
                            IndexDocumentResponse(
                                success = result.success,
                                documentId = result.documentId,
                                chunksCount = result.chunksCount,
                                error = result.error,
                                errorsCount = result.errorsCount
                            )
                        }
                    ))
                } catch (e: Exception) {
                    logger.error("Directory indexing error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to index directory: ${e.message}")
                    )
                }
            }
            
            // Статус индексации (статистика)
            get("/api/indexing/status") {
                val statistics = knowledgeBaseRepository.getStatistics()
                call.respond(IndexingStatusResponse(
                    documentsCount = statistics.documentsCount,
                    chunksCount = statistics.chunksCount
                ))
            }
            
            // Список индексированных документов
            get("/api/indexing/documents") {
                val documents = knowledgeBaseRepository.getAllDocuments()
                call.respond(DocumentsListResponse(
                    documents = documents.map { doc ->
                        DocumentInfo(
                            id = doc.id,
                            filePath = doc.filePath,
                            title = doc.title,
                            indexedAt = doc.indexedAt,
                            chunkCount = doc.chunkCount
                        )
                    }
                ))
            }
        }
    }
}

@Serializable
data class IndexDocumentRequest(
    val filePath: String
)

@Serializable
data class IndexDocumentResponse(
    val success: Boolean,
    val documentId: String,
    val chunksCount: Int,
    val error: String? = null,
    val errorsCount: Int = 0
)

@Serializable
data class IndexDirectoryRequest(
    val directoryPath: String
)

@Serializable
data class IndexDirectoryResponse(
    val success: Boolean,
    val documentsProcessed: Int,
    val documentsSucceeded: Int,
    val totalChunks: Int,
    val results: List<IndexDocumentResponse>
)

@Serializable
data class IndexingStatusResponse(
    val documentsCount: Int,
    val chunksCount: Int
)



