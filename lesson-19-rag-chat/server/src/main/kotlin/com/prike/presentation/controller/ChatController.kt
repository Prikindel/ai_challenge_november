package com.prike.presentation.controller

import com.prike.data.repository.ChatRepository
import com.prike.domain.model.MessageRole
import com.prike.domain.service.ChatService
import com.prike.presentation.dto.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

/**
 * Контроллер для API чата
 */
class ChatController(
    private val chatService: ChatService,
    private val chatRepository: ChatRepository
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Создать новую сессию
            post("/api/chat/sessions") {
                try {
                    val request = call.receive<CreateSessionRequest>()
                    val session = chatRepository.createSession(title = request.title)
                    
                    logger.info("Created new chat session: ${session.id}")
                    call.respond(
                        HttpStatusCode.Created,
                        ChatSessionDto(
                            id = session.id,
                            title = session.title,
                            createdAt = session.createdAt,
                            updatedAt = session.updatedAt
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to create session", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to create session: ${e.message}")
                    )
                }
            }
            
            // Получить список всех сессий
            get("/api/chat/sessions") {
                try {
                    val sessions = chatRepository.getAllSessions()
                    val sessionsDto = sessions.map { session ->
                        ChatSessionDto(
                            id = session.id,
                            title = session.title,
                            createdAt = session.createdAt,
                            updatedAt = session.updatedAt
                        )
                    }
                    
                    call.respond(sessionsDto)
                } catch (e: Exception) {
                    logger.error("Failed to get sessions", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get sessions: ${e.message}")
                    )
                }
            }
            
            // Получить сессию по ID
            get("/api/chat/sessions/{sessionId}") {
                try {
                    val sessionId = call.parameters["sessionId"]
                        ?: throw IllegalArgumentException("sessionId is required")
                    
                    val session = chatRepository.getSession(sessionId)
                        ?: run {
                            call.respond(
                                HttpStatusCode.NotFound,
                                ErrorResponse("Session not found: $sessionId")
                            )
                            return@get
                        }
                    
                    call.respond(
                        ChatSessionDto(
                            id = session.id,
                            title = session.title,
                            createdAt = session.createdAt,
                            updatedAt = session.updatedAt
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to get session", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get session: ${e.message}")
                    )
                }
            }
            
            // Удалить сессию
            delete("/api/chat/sessions/{sessionId}") {
                try {
                    val sessionId = call.parameters["sessionId"]
                        ?: throw IllegalArgumentException("sessionId is required")
                    
                    val session = chatRepository.getSession(sessionId)
                    if (session == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("Session not found: $sessionId")
                        )
                        return@delete
                    }
                    
                    chatRepository.deleteSession(sessionId)
                    logger.info("Deleted chat session: $sessionId")
                    
                    // Возвращаем 200 OK с пустым объектом для совместимости
                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } catch (e: Exception) {
                    logger.error("Failed to delete session", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to delete session: ${e.message}")
                    )
                }
            }
            
            // Отправить сообщение в сессию
            post("/api/chat/sessions/{sessionId}/messages") {
                try {
                    val sessionId = call.parameters["sessionId"]
                        ?: throw IllegalArgumentException("sessionId is required")
                    
                    val request = call.receive<SendMessageRequest>()
                    
                    // Валидация
                    if (request.message.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("message cannot be blank")
                        )
                        return@post
                    }
                    
                    // Валидация стратегии истории
                    val historyStrategy = request.historyStrategy?.takeIf { 
                        it in listOf("sliding", "token_limit", "none") 
                    }
                    
                    // Обрабатываем сообщение через ChatService
                    val assistantMessage = chatService.processMessage(
                        sessionId = sessionId,
                        userMessage = request.message,
                        topK = request.topK.coerceIn(1, 10),
                        minSimilarity = request.minSimilarity.coerceIn(0f, 1f),
                        applyFilter = request.applyFilter,
                        strategy = request.strategy?.takeIf { it in listOf("none", "threshold", "reranker", "hybrid") },
                        historyStrategy = historyStrategy
                    )
                    
                    logger.info("Message processed: session=$sessionId, messageId=${assistantMessage.id}")
                    
                    call.respond(
                        SendMessageResponse(
                            message = ChatMessageDto(
                                id = assistantMessage.id,
                                sessionId = assistantMessage.sessionId,
                                role = assistantMessage.role.name,
                                content = assistantMessage.content,
                                citations = assistantMessage.citations.map { citation ->
                                    MessageCitationDto(
                                        text = citation.text,
                                        documentPath = citation.documentPath,
                                        documentTitle = citation.documentTitle,
                                        chunkId = citation.chunkId
                                    )
                                },
                                createdAt = assistantMessage.createdAt
                            ),
                            sessionId = sessionId
                        )
                    )
                } catch (e: IllegalArgumentException) {
                    logger.warn("Invalid request: ${e.message}")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(e.message ?: "Invalid request")
                    )
                } catch (e: Exception) {
                    logger.error("Failed to process message", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to process message: ${e.message}")
                    )
                }
            }
            
            // Получить историю сообщений сессии
            get("/api/chat/sessions/{sessionId}/messages") {
                try {
                    val sessionId = call.parameters["sessionId"]
                        ?: throw IllegalArgumentException("sessionId is required")
                    
                    // Проверяем существование сессии
                    val session = chatRepository.getSession(sessionId)
                    if (session == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("Session not found: $sessionId")
                        )
                        return@get
                    }
                    
                    // Получаем историю
                    val messages = chatService.getHistory(sessionId)
                    val messagesDto = messages.map { message ->
                        ChatMessageDto(
                            id = message.id,
                            sessionId = message.sessionId,
                            role = message.role.name,
                            content = message.content,
                            citations = message.citations.map { citation ->
                                MessageCitationDto(
                                    text = citation.text,
                                    documentPath = citation.documentPath,
                                    documentTitle = citation.documentTitle,
                                    chunkId = citation.chunkId
                                )
                            },
                            createdAt = message.createdAt
                        )
                    }
                    
                    call.respond(
                        ChatHistoryResponse(
                            sessionId = sessionId,
                            messages = messagesDto
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to get history", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get history: ${e.message}")
                    )
                }
            }
        }
    }
}

