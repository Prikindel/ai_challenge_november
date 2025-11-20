package com.prike.presentation.controller

import com.prike.domain.agent.LLMCompositionAgent
import com.prike.presentation.dto.ChatMessageRequestDto
import com.prike.presentation.dto.ChatMessageResponseDto
import com.prike.presentation.dto.ErrorDto
import com.prike.presentation.dto.ToolCallDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Контроллер для чата с LLM агентом
 * Обрабатывает длительные операции в неблокирующем режиме
 */
class ChatController(
    private val llmCompositionAgent: LLMCompositionAgent
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.route("/api/chat") {
            post("/message") {
                call.handleUserMessage()
            }
        }
    }
    
    private suspend fun ApplicationCall.handleUserMessage() {
        try {
            val request = receive<ChatMessageRequestDto>()
            
            if (request.message.isBlank()) {
                respond(
                    HttpStatusCode.BadRequest,
                    ErrorDto(message = "Сообщение не может быть пустым")
                )
                return
            }
            
            // Запускаем обработку в отдельной корутине (не блокируем)
            // Используем Dispatchers.Default для CPU-интенсивных операций
            val response = withContext(Dispatchers.Default) {
                llmCompositionAgent.processUserMessage(request.message)
            }
            
            when (response) {
                is LLMCompositionAgent.AgentResponse.Success -> {
                    respond(
                        HttpStatusCode.OK,
                        ChatMessageResponseDto(
                            message = response.message,
                            toolCalls = response.toolCalls.map { toolCall ->
                                ToolCallDto(
                                    name = toolCall.name,
                                    success = toolCall.success
                                )
                            }
                        )
                    )
                }
                is LLMCompositionAgent.AgentResponse.Error -> {
                    logger.error("Error in chat: ${response.message}", response.cause)
                    respond(
                        HttpStatusCode.InternalServerError,
                        ErrorDto(message = response.message)
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing chat request: ${e.message}", e)
            respond(
                HttpStatusCode.InternalServerError,
                ErrorDto(message = "Ошибка обработки: ${e.message}")
            )
        }
    }
}

