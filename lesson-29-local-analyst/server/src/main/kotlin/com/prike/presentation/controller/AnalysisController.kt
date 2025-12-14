package com.prike.presentation.controller

import com.prike.data.repository.DataRepository
import com.prike.domain.service.DataAnalysisService
import com.prike.presentation.dto.AnalysisRequest
import com.prike.presentation.dto.AnalysisResponse
import com.prike.presentation.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

/**
 * Контроллер для аналитических вопросов
 */
class AnalysisController(
    private val dataAnalysisService: DataAnalysisService,
    private val dataRepository: DataRepository
) {
    private val logger = LoggerFactory.getLogger(AnalysisController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Аналитический вопрос
            post("/api/analyze") {
                try {
                    val request = call.receive<AnalysisRequest>()
                    
                    if (request.question.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Question cannot be empty")
                        )
                        return@post
                    }
                    
                    // Нормализуем source: если null или пустой, используем "reviews"
                    val source = request.source?.takeIf { it.isNotBlank() } ?: "reviews"
                    
                    logger.info("Analysis request: question='${request.question}', source=$source, limit=${request.limit}")
                    
                    // Получаем количество записей для ответа
                    val recordsCount = dataRepository.getRecordsCountBySource(source)
                    
                    // Анализируем вопрос
                    val answer = dataAnalysisService.analyzeQuestion(
                        question = request.question,
                        source = source,
                        limit = request.limit ?: 1000
                    )
                    
                    call.respond(
                        HttpStatusCode.OK,
                        AnalysisResponse(
                            question = request.question,
                            answer = answer,
                            source = source,
                            recordsCount = recordsCount
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error analyzing question: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Error analyzing question: ${e.message}")
                    )
                }
            }
        }
    }
}
