package com.prike.presentation.controller

import com.prike.domain.service.ReviewsAnalysisService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Контроллер для API работы с отзывами и анализом
 */
class ReviewsController(
    private val analysisService: ReviewsAnalysisService,
    private val reviewsTools: com.prike.domain.tools.ReviewsTools? = null // Для батчинга по дням
) {
    private val logger = LoggerFactory.getLogger(ReviewsController::class.java)

    fun registerRoutes(routing: Routing) {
        routing.route("/api/reviews") {
            // POST /api/reviews/analyze-batch - запуск анализа с автоматическим батчингом по дням
            post("/analyze-batch") {
                try {
                    val request = call.receive<AnalyzeReviewsRequest>()
                    logger.info("Received batch analyze request: fromDate=${request.fromDate}, toDate=${request.toDate}")

                    // Используем новый метод analyzePeriodByDays через ReviewsTools
                    if (reviewsTools == null) {
                        throw IllegalStateException("ReviewsTools not available for batch analysis")
                    }
                    
                    val resultJson = kotlinx.coroutines.runBlocking {
                        reviewsTools.analyzePeriodByDays(
                            fromDate = request.fromDate ?: throw IllegalArgumentException("fromDate is required"),
                            toDate = request.toDate ?: throw IllegalArgumentException("toDate is required")
                        )
                    }
                    
                    val result = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        .decodeFromString<BatchAnalysisResultDto>(resultJson)

                    call.respond(
                        HttpStatusCode.OK,
                        BatchAnalysisResponse(
                            success = result.success,
                            message = result.message ?: "Batch analysis completed: processed ${result.totalProcessed} reviews, saved ${result.totalSaved} summaries for ${result.daysProcessed} days",
                            totalProcessed = result.totalProcessed,
                            totalSaved = result.totalSaved,
                            batchesProcessed = result.daysProcessed, // Используем daysProcessed как batchesProcessed
                            daysProcessed = result.daysProcessed
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error in batch analysis: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        BatchAnalysisResponse(
                            success = false,
                            message = "Error: ${e.message}",
                            totalProcessed = 0,
                            totalSaved = 0,
                            batchesProcessed = 0
                        )
                    )
                }
            }
            
            // POST /api/reviews/analyze - запуск анализа недели
            post("/analyze") {
                try {
                    val request = call.receive<AnalyzeReviewsRequest>()
                    logger.info("Received analyze request: fromDate=${request.fromDate}, toDate=${request.toDate}")

                    val stats = analysisService.analyzeWeek(
                        fromDate = request.fromDate,
                        toDate = request.toDate
                    )

                    call.respond(
                        HttpStatusCode.OK,
                        AnalysisResponse(
                            success = true,
                            message = "Analysis completed successfully",
                            weekStart = stats.weekStart,
                            stats = stats
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error analyzing reviews: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        AnalysisResponse(
                            success = false,
                            message = "Error: ${e.message}",
                            weekStart = null,
                            stats = null
                        )
                    )
                }
            }

            // GET /api/reviews/week/{weekStart} - получение анализа недели
            get("/week/{weekStart}") {
                try {
                    val weekStart = call.parameters["weekStart"]
                        ?: throw IllegalArgumentException("weekStart parameter is required")

                    logger.info("Getting week analysis for: $weekStart")

                    val stats = analysisService.getWeekAnalysis(weekStart)

                    if (stats != null) {
                        call.respond(
                            HttpStatusCode.OK,
                            WeekAnalysisResponse(
                                success = true,
                                stats = stats
                            )
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            WeekAnalysisResponse(
                                success = false,
                                stats = null
                            )
                        )
                    }
                } catch (e: Exception) {
                    logger.error("Error getting week analysis: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        WeekAnalysisResponse(
                            success = false,
                            stats = null
                        )
                    )
                }
            }

            // GET /api/reviews/stats - получение общей статистики
            get("/stats") {
                try {
                    logger.info("Getting reviews stats")
                    // TODO: Реализовать получение общей статистики из БД
                    // Пока возвращаем базовый ответ
                    call.respond(
                        HttpStatusCode.OK,
                        StatsResponse(
                            success = true,
                            message = "Stats endpoint - to be implemented"
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error getting stats: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        StatsResponse(
                            success = false,
                            message = "Error: ${e.message}"
                        )
                    )
                }
            }

            // POST /api/reviews/index-all-rag - индексация всех саммари в RAG
            post("/index-all-rag") {
                try {
                    logger.info("RAG indexing request for all summaries")
                    
                    if (reviewsTools == null) {
                        throw IllegalStateException("ReviewsTools not available")
                    }
                    
                    val resultJson = kotlinx.coroutines.runBlocking {
                        reviewsTools.indexAllSummaries()
                    }
                    
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    val result = json.decodeFromString<kotlinx.serialization.json.JsonObject>(resultJson)
                    
                    val success = result["success"]?.jsonPrimitive?.content == "true"
                    val statusCode = if (success) HttpStatusCode.OK else HttpStatusCode.InternalServerError
                    
                    call.respond(statusCode, result)
                } catch (e: Exception) {
                    logger.error("Error indexing all summaries in RAG: ${e.message}", e)
                    val errorResponse = kotlinx.serialization.json.buildJsonObject {
                        put("success", false)
                        put("error", e.message ?: "Unknown error")
                    }
                    call.respond(HttpStatusCode.InternalServerError, errorResponse)
                }
            }
        }
    }
}

/**
 * DTO для запроса анализа
 */
@Serializable
data class AnalyzeReviewsRequest(
    val fromDate: String? = null,
    val toDate: String? = null
)

/**
 * DTO для ответа анализа
 */
@Serializable
data class AnalysisResponse(
    val success: Boolean,
    val message: String,
    val weekStart: String?,
    val stats: com.prike.domain.model.WeekStats?
)

/**
 * DTO для ответа анализа недели
 */
@Serializable
data class WeekAnalysisResponse(
    val success: Boolean,
    val stats: com.prike.domain.model.WeekStats?
)

/**
 * DTO для ответа статистики
 */
@Serializable
data class StatsResponse(
    val success: Boolean,
    val message: String
)

/**
 * DTO для ответа батчингового анализа
 */
@Serializable
data class BatchAnalysisResponse(
    val success: Boolean,
    val message: String,
    val totalProcessed: Int,
    val totalSaved: Int,
    val batchesProcessed: Int,
    val daysProcessed: Int = 0
)

/**
 * DTO для результата батчингового анализа (из JSON ответа)
 */
@Serializable
data class BatchAnalysisResultDto(
    val success: Boolean,
    val message: String? = null,
    val totalProcessed: Int,
    val totalSaved: Int,
    val daysProcessed: Int,
    val processedDays: List<String> = emptyList(),
    val error: String? = null
)

