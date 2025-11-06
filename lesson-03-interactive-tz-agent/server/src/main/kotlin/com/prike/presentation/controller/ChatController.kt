package com.prike.presentation.controller

import com.prike.domain.agent.TZAgent
import com.prike.domain.exception.AIServiceException
import com.prike.domain.exception.DomainException
import com.prike.domain.exception.ValidationException
import com.prike.presentation.dto.ChatHistoryDto
import com.prike.presentation.dto.ChatMessageDto
import com.prike.presentation.dto.ChatResponseDto
import com.prike.presentation.dto.ErrorResponseDto
import com.prike.presentation.dto.JsonHistoryEntryDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

/**
 * Контроллер для интерактивного чата со сбором ТЗ
 */
class ChatController(
    private val tzAgent: TZAgent
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)
    
    fun configureRoutes(routing: Routing) {
        routing.route("/chat") {
            post {
                call.handleChatMessage()
            }
            
            delete {
                call.handleClearHistory()
            }
            
            get("/history") {
                call.handleGetHistory()
            }
        }

        routing.get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
    }
    
    /**
     * Обработка сообщения в чат
     */
    private suspend fun ApplicationCall.handleChatMessage() {
        try {
            val request = receive<ChatMessageDto>()
            
            // Валидация
            if (request.message.isBlank()) {
                respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponseDto("Сообщение не может быть пустым")
                )
                return
            }
            
            if (request.message.length > 2000) {
                respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponseDto("Сообщение слишком длинное (максимум 2000 символов)")
                )
                return
            }
            
            // Обрабатываем сообщение через агента
            val result = tzAgent.processMessage(request.message.trim())
            
            // Формируем ответ
            val response = when (result) {
                is TZAgent.TZAgentResult.Continue -> {
                    ChatResponseDto.Continue(
                        message = result.message,
                        debug = ChatResponseDto.DebugInfo(
                            llmRequest = result.requestJson,
                            llmResponse = result.responseJson
                        )
                    )
                }
                is TZAgent.TZAgentResult.TZReady -> {
                    ChatResponseDto.TZReady(
                        technicalSpec = result.technicalSpec,
                        debug = ChatResponseDto.DebugInfo(
                            llmRequest = result.requestJson,
                            llmResponse = result.responseJson
                        )
                    )
                }
            }
            
            respond(HttpStatusCode.OK, response)
            
        } catch (e: DomainException) {
            logger.error("Domain error: ${e.message}", e)
            val (statusCode, errorResponse) = mapErrorToHttpResponse(e)
            respond(statusCode, errorResponse)
            
        } catch (e: Exception) {
            logger.error("Unexpected error", e)
            respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto("Внутренняя ошибка сервера: ${e.message ?: "Неизвестная ошибка"}")
            )
        }
    }
    
    /**
     * Очистка истории сообщений (начать новый диалог)
     */
    private suspend fun ApplicationCall.handleClearHistory() {
        try {
            tzAgent.clearHistory()
            respond(HttpStatusCode.OK, mapOf("status" to "ok", "message" to "История очищена"))
        } catch (e: Exception) {
            logger.error("Error clearing history", e)
            respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto("Ошибка при очистке истории: ${e.message ?: "Неизвестная ошибка"}")
            )
        }
    }
    
    /**
     * Получение истории JSON запросов и ответов
     */
    private suspend fun ApplicationCall.handleGetHistory() {
        try {
            val jsonHistory = tzAgent.getJsonHistory()
            val historyDto = ChatHistoryDto(
                entries = jsonHistory.map { entry ->
                    JsonHistoryEntryDto(
                        requestJson = entry.requestJson,
                        responseJson = entry.responseJson
                    )
                }
            )
            respond(HttpStatusCode.OK, historyDto)
        } catch (e: Exception) {
            logger.error("Error getting history", e)
            respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto("Ошибка при получении истории: ${e.message ?: "Неизвестная ошибка"}")
            )
        }
    }
    
    /**
     * Маппинг доменных исключений в HTTP ответы
     */
    private fun mapErrorToHttpResponse(exception: DomainException): Pair<HttpStatusCode, ErrorResponseDto> {
        return when (exception) {
            is ValidationException -> {
                HttpStatusCode.BadRequest to ErrorResponseDto(exception.message ?: "Ошибка валидации")
            }
            is AIServiceException -> {
                HttpStatusCode.InternalServerError to ErrorResponseDto(
                    "Ошибка при обращении к AI сервису: ${exception.message}"
                )
            }
            else -> {
                HttpStatusCode.InternalServerError to ErrorResponseDto(
                    "Внутренняя ошибка сервера: ${exception.message}"
                )
            }
        }
    }
}

