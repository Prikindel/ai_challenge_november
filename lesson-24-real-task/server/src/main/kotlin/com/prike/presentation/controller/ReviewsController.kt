package com.prike.presentation.controller

import com.prike.domain.service.ReviewsAnalysisService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Контроллер для API работы с отзывами и анализом
 */
class ReviewsController(
    private val analysisService: ReviewsAnalysisService
) {
    private val logger = LoggerFactory.getLogger(ReviewsController::class.java)

    fun registerRoutes(routing: Routing) {
        routing.route("/api/reviews") {
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

