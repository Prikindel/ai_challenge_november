package com.prike.presentation.controller

import com.prike.data.repository.DataRepository
import com.prike.presentation.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Контроллер для загрузки данных
 */
class DataController(
    private val dataRepository: DataRepository
) {
    private val logger = LoggerFactory.getLogger(DataController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Загрузка данных отключена - используется БД из урока 24
            // Endpoints оставлены для совместимости, но возвращают ошибку
            
            // Загрузка CSV файла (отключена)
            post("/api/data/upload/csv") {
                call.respond(
                    HttpStatusCode.NotImplemented,
                    ErrorResponse("Загрузка данных отключена. Используется БД из урока 24.")
                )
            }
            
            // Загрузка JSON файла (отключена)
            post("/api/data/upload/json") {
                call.respond(
                    HttpStatusCode.NotImplemented,
                    ErrorResponse("Загрузка данных отключена. Используется БД из урока 24.")
                )
            }
            
            // Загрузка файла логов (отключена)
            post("/api/data/upload/logs") {
                call.respond(
                    HttpStatusCode.NotImplemented,
                    ErrorResponse("Загрузка данных отключена. Используется БД из урока 24.")
                )
            }
            
            // Получение статистики по данным
            get("/api/data/stats") {
                try {
                    val totalCount = dataRepository.getTotalRecordsCount()
                    val reviewsCount = dataRepository.getRecordsCountBySource("reviews")
                    val positiveCount = dataRepository.getRecordsCountBySource("positive")
                    val negativeCount = dataRepository.getRecordsCountBySource("negative")
                    val neutralCount = dataRepository.getRecordsCountBySource("neutral")
                    
                    call.respond(
                        HttpStatusCode.OK,
                        DataStatsResponse(
                            total = totalCount,
                            bySource = mapOf(
                                "reviews" to reviewsCount,
                                "positive" to positiveCount,
                                "negative" to negativeCount,
                                "neutral" to neutralCount
                            )
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error getting data stats: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Error getting data stats: ${e.message}")
                    )
                }
            }
        }
    }
}

@Serializable
data class UploadResponse(
    val filename: String,
    val recordsCount: Int,
    val source: String
)

@Serializable
data class DataStatsResponse(
    val total: Int,
    val bySource: Map<String, Int>
)
