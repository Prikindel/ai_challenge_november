package com.prike.presentation.controller

import com.prike.domain.model.DocumentCategory
import com.prike.domain.service.KnowledgeBaseService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable as KSerializable
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Контроллер для управления базой знаний
 */
class KnowledgeBaseController(
    private val knowledgeBaseService: KnowledgeBaseService,
    private val lessonRoot: File
) {
    private val logger = LoggerFactory.getLogger(KnowledgeBaseController::class.java)
    
    fun registerRoutes(application: Application) {
        application.routing {
        route("/api/knowledge-base") {
            /**
             * Индексировать всю базу знаний
             */
            post("/index") {
                try {
                    logger.info("Starting knowledge base indexing")
                    knowledgeBaseService.indexKnowledgeBase(lessonRoot)
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "status" to "success",
                            "message" to "Knowledge base indexed successfully"
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to index knowledge base: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf<String, String>(
                            "status" to "error",
                            "message" to (e.message ?: "Unknown error")
                        )
                    )
                }
            }
            
            /**
             * Поиск в базе знаний
             */
            get("/search") {
                val query = call.request.queryParameters["query"] ?: ""
                val category = call.request.queryParameters["category"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 5
                
                if (query.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Query parameter is required")
                    )
                    return@get
                }
                
                try {
                    val results = knowledgeBaseService.searchInCategory(query, category, limit)
                    call.respond(
                        HttpStatusCode.OK,
                        SearchResponse(
                            query = query,
                            category = category,
                            results = results.map { chunk ->
                                SearchResult(
                                    id = chunk.id,
                                    text = chunk.text,
                                    similarity = chunk.similarity,
                                    source = chunk.source,
                                    category = chunk.category.displayName
                                )
                            }
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to search knowledge base: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Unknown error"))
                    )
                }
            }
            
            /**
             * Получить список категорий
             */
            get("/categories") {
                val categories = DocumentCategory.values().map { category ->
                    CategoryInfo(
                        name = category.name,
                        displayName = category.displayName,
                        path = category.path
                    )
                }
                call.respond(HttpStatusCode.OK, CategoriesResponse(categories))
            }
            
            /**
             * Получить статистику базы знаний
             */
            get("/statistics") {
                try {
                    val stats = knowledgeBaseService.getStatistics()
                    call.respond(
                        HttpStatusCode.OK,
                        StatisticsResponse(
                            totalChunks = stats.totalChunks,
                            chunksByCategory = stats.chunksByCategory
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to get statistics: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Unknown error"))
                    )
                }
            }
            
            /**
             * Индексировать конкретную категорию
             */
            post("/index/category/{category}") {
                val categoryName = call.parameters["category"]
                    ?: run {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Category parameter is required")
                        )
                        return@post
                    }
                
                val category = DocumentCategory.fromName(categoryName)
                    ?: run {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid category: $categoryName")
                        )
                        return@post
                    }
                
                try {
                    val categoryPath = File(lessonRoot, "knowledge-base/${category.path}")
                    val indexedCount = knowledgeBaseService.indexCategory(category, categoryPath)
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "status" to "success",
                            "category" to category.displayName,
                            "indexedCount" to indexedCount
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to index category $categoryName: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Unknown error"))
                    )
                }
            }
        }
        }
    }
}

@KSerializable
data class SearchResponse(
    val query: String,
    val category: String?,
    val results: List<SearchResult>
)

@KSerializable
data class SearchResult(
    val id: String,
    val text: String,
    val similarity: Double,
    val source: String,
    val category: String
)

@KSerializable
data class CategoriesResponse(
    val categories: List<CategoryInfo>
)

@KSerializable
data class CategoryInfo(
    val name: String,
    val displayName: String,
    val path: String
)

@KSerializable
data class StatisticsResponse(
    val totalChunks: Int,
    val chunksByCategory: Map<String, Int>
)

