package com.prike.presentation.controller

import com.prike.domain.agent.LLMWithMCPAgent
import com.prike.presentation.dto.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

/**
 * Контроллер для общения с LLM агентом через чат
 */
class ChatController(
    private val llmWithMCPAgent: LLMWithMCPAgent
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)
    
    fun configureRoutes(routing: Routing) {
        routing.route("/api/chat") {
            // POST /api/chat/message - отправить сообщение LLM агенту
            post("/message") {
                call.handleUserMessage()
            }
        }
    }
    
    private suspend fun ApplicationCall.handleUserMessage() {
        try {
            val request = receive<ChatMessageRequestDto>()
            
            if (request.message.isBlank()) {
                respond(HttpStatusCode.BadRequest, ErrorDto(
                    message = "Сообщение не может быть пустым"
                ))
                return
            }
            
            val response = llmWithMCPAgent.processUserMessage(request.message)
            
            when (response) {
                is LLMWithMCPAgent.AgentResponse.Success -> {
                    respond(HttpStatusCode.OK, ChatMessageResponseDto(
                        message = response.message,
                        toolUsed = response.toolUsed,
                        toolResult = response.toolResult
                    ))
                }
                is LLMWithMCPAgent.AgentResponse.Error -> {
                    logger.error("Error processing chat message: ${response.message}", response.cause)
                    respond(HttpStatusCode.InternalServerError, ErrorDto(
                        message = response.message
                    ))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error in chat controller: ${e.message}", e)
            respond(HttpStatusCode.InternalServerError, ErrorDto(
                message = e.message ?: "Неизвестная ошибка"
            ))
        }
    }
}

