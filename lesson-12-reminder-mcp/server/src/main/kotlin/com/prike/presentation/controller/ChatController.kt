package com.prike.presentation.controller

import com.prike.domain.agent.LLMWithSummaryAgent
import com.prike.presentation.dto.ChatRequestDto
import com.prike.presentation.dto.ChatResponseDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

/**
 * Контроллер для чата с LLM агентом
 */
class ChatController(
    private val llmAgent: LLMWithSummaryAgent
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.route("/api/chat") {
            post {
                try {
                    val request = call.receive<ChatRequestDto>()
                    
                    if (request.message.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ChatResponseDto(
                                success = false,
                                error = "Сообщение не может быть пустым"
                            )
                        )
                        return@post
                    }
                    
                    val response = llmAgent.processUserMessage(request.message)
                    
                    when (response) {
                        is LLMWithSummaryAgent.AgentResponse.Success -> {
                            call.respond(
                                HttpStatusCode.OK,
                                ChatResponseDto(
                                    success = true,
                                    message = response.message,
                                    toolUsed = response.toolUsed
                                )
                            )
                        }
                        is LLMWithSummaryAgent.AgentResponse.Error -> {
                            logger.error("Error in chat: ${response.message}", response.cause)
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ChatResponseDto(
                                    success = false,
                                    error = response.message
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error processing chat request: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ChatResponseDto(
                            success = false,
                            error = e.message ?: "Unknown error"
                        )
                    )
                }
            }
        }
    }
}

