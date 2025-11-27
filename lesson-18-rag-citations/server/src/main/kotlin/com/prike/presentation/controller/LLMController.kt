package com.prike.presentation.controller

import com.prike.domain.service.LLMService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.prike.presentation.dto.ErrorResponse
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Контроллер для API работы с LLM
 */
class LLMController(
    private val llmService: LLMService
) {
    private val logger = LoggerFactory.getLogger(LLMController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Обычный запрос к LLM (без контекста)
            post("/api/llm/chat") {
                try {
                    val request = call.receive<LLMChatRequest>()
                    
                    // Валидация входных данных
                    if (request.question.isBlank()) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("question cannot be blank")
                        )
                        return@post
                    }
                    
                    val response = llmService.generateAnswer(
                        question = request.question,
                        systemPrompt = request.systemPrompt
                    )
                    
                    call.respond(LLMChatResponse(
                        question = request.question,
                        answer = response.answer,
                        tokensUsed = response.tokensUsed
                    ))
                } catch (e: Exception) {
                    logger.error("LLM chat error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get answer from LLM: ${e.message}")
                    )
                }
            }
        }
    }
}

@Serializable
data class LLMChatRequest(
    val question: String,
    val systemPrompt: String? = null
)

@Serializable
data class LLMChatResponse(
    val question: String,
    val answer: String,
    val tokensUsed: Int
)

