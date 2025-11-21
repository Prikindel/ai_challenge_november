package com.prike.presentation.controller

import com.prike.domain.agent.OrchestrationAgent
import com.prike.presentation.dto.ChatMessageRequestDto
import com.prike.presentation.dto.ChatMessageResponseDto
import com.prike.presentation.dto.ErrorDto
import com.prike.presentation.dto.ToolCallInfoDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/**
 * Контроллер для чата с LLM агентом
 * Обрабатывает длительные операции в неблокирующем режиме
 */
class ChatController(
    private val orchestrationAgent: OrchestrationAgent
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)
    
    companion object {
        // Таймаут для обработки сообщения (120 секунд)
        private const val PROCESSING_TIMEOUT_MS = 120_000L
    }
    
    fun registerRoutes(routing: Routing) {
        routing.route("/api/chat") {
            post("/message") {
                call.handleUserMessage()
            }
        }
    }
    
    private suspend fun ApplicationCall.handleUserMessage() {
        val startTime = System.currentTimeMillis()
        
        try {
            val request = receive<ChatMessageRequestDto>()
            
            if (request.message.isBlank()) {
                respond(
                    HttpStatusCode.BadRequest,
                    ErrorDto(message = "Сообщение не может быть пустым")
                )
                return
            }
            
            logger.info("Обработка сообщения пользователя: ${request.message.take(100)}...")
            
            // Запускаем обработку в отдельной корутине с таймаутом
            // Используем Dispatchers.Default для CPU-интенсивных операций
            val response = withContext(Dispatchers.Default) {
                withTimeout(PROCESSING_TIMEOUT_MS) {
                    orchestrationAgent.processUserMessage(request.message)
                }
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            
            when (response) {
                is OrchestrationAgent.AgentResponse.Success -> {
                    logger.info("Сообщение обработано успешно за ${processingTime}мс, вызвано инструментов: ${response.toolCalls.size}")
                    
                    // Находим serverId для каждого инструмента
                    val toolCallsWithServer = response.toolCalls.map { toolCall ->
                        // serverId будет добавлен позже через MCPClientManager
                        ToolCallInfoDto(
                            toolName = toolCall.name,
                            serverId = null, // Можно добавить через MCPClientManager.findServerForTool
                            success = toolCall.success,
                            result = if (toolCall.success) toolCall.result.take(200) else null
                        )
                    }
                    
                    respond(
                        HttpStatusCode.OK,
                        ChatMessageResponseDto(
                            message = response.message,
                            toolCalls = toolCallsWithServer,
                            processingTime = processingTime
                        )
                    )
                }
                is OrchestrationAgent.AgentResponse.Error -> {
                    logger.error("Ошибка в чате: ${response.message}", response.cause)
                    respond(
                        HttpStatusCode.InternalServerError,
                        ErrorDto(message = response.message)
                    )
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val processingTime = System.currentTimeMillis() - startTime
            logger.error("Таймаут обработки сообщения (${processingTime}мс)")
            respond(
                HttpStatusCode.RequestTimeout,
                ErrorDto(message = "Превышено время ожидания обработки сообщения (${PROCESSING_TIMEOUT_MS / 1000} секунд)")
            )
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            logger.error("Ошибка обработки запроса чата: ${e.message}", e)
            respond(
                HttpStatusCode.InternalServerError,
                ErrorDto(message = "Ошибка обработки: ${e.message}")
            )
        }
    }
}

