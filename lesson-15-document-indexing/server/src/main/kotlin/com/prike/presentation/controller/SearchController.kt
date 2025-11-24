package com.prike.presentation.controller

import com.prike.domain.service.KnowledgeBaseSearchService
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
 * Контроллер для API поиска по базе знаний
 */
class SearchController(
    private val searchService: KnowledgeBaseSearchService,
    private val knowledgeBaseRepository: KnowledgeBaseRepository
) {
    private val logger = LoggerFactory.getLogger(SearchController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Поиск по запросу
            post("/api/search/query") {
                try {
                    val request = call.receive<SearchRequest>()
                    
                    // Валидация входных данных
                    if (request.query.isBlank()) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("query cannot be blank")
                        )
                        return@post
                    }
                    
                    val limit = (request.limit ?: 10).coerceIn(1, 50) // Ограничиваем до 50
                    val results = searchService.search(
                        query = request.query,
                        limit = limit
                    )
                    
                    call.respond(SearchResponse(
                        success = true,
                        query = request.query,
                        results = results.map { result ->
                            SearchResultDto(
                                chunkId = result.chunkId,
                                documentId = result.documentId,
                                content = result.content,
                                similarity = result.similarity,
                                chunkIndex = result.chunkIndex,
                                documentTitle = result.documentTitle,
                                documentFilePath = result.documentFilePath
                            )
                        }
                    ))
                } catch (e: Exception) {
                    logger.error("Search error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to search: ${e.message}")
                    )
                }
            }
            
            // Список документов (для UI)
            get("/api/search/documents") {
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
data class SearchRequest(
    val query: String,
    val limit: Int? = null
)

@Serializable
data class SearchResponse(
    val success: Boolean,
    val query: String,
    val results: List<SearchResultDto>
)

@Serializable
data class SearchResultDto(
    val chunkId: String,
    val documentId: String,
    val content: String,
    val similarity: Float,
    val chunkIndex: Int,
    val documentTitle: String? = null,
    val documentFilePath: String? = null
)



