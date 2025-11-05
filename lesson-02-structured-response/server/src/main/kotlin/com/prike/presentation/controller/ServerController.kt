package com.prike.presentation.controller

import com.prike.domain.exception.AIServiceException
import com.prike.domain.exception.DomainException
import com.prike.domain.exception.ValidationException
import com.prike.domain.usecase.ChatUseCase
import com.prike.presentation.dto.ChatRequestDto
import com.prike.presentation.dto.ChatResponseDto
import com.prike.presentation.dto.ErrorResponseDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

/**
 * Контроллер для обработки HTTP запросов чата
 */
class ServerController(
    private val chatUseCase: ChatUseCase
) {
    private val logger = LoggerFactory.getLogger(ServerController::class.java)
    
    /**
     * Настройка маршрутов
     */
    fun configureRoutes(routing: Routing) {
        // API endpoints
        routing.route("/chat") {
            post {
                call.handleChatRequest()
            }
        }
        
        routing.get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
    }
    
    /**
     * Обработка запроса на отправку сообщения
     */
    private suspend fun ApplicationCall.handleChatRequest() {
        try {
            val request = receive<ChatRequestDto>()
            val aiResponse = chatUseCase.processMessage(request.message)
            respond(HttpStatusCode.OK, ChatResponseDto(response = aiResponse))
            
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
