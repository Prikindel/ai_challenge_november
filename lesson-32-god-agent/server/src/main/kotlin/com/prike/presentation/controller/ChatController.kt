package com.prike.presentation.controller

import com.prike.data.repository.ChatRepository
import com.prike.domain.model.MessageRole
import com.prike.domain.service.AudioConversionService
import com.prike.domain.service.ReviewsChatService
import com.prike.domain.service.SpeechRecognitionService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Контроллер для API чата
 */
class ChatController(
    private val chatService: ReviewsChatService,
    private val chatRepository: ChatRepository,
    private val speechRecognitionService: SpeechRecognitionService? = null,
    private val audioConversionService: AudioConversionService? = null,
    private val godAgentService: com.prike.domain.service.GodAgentService? = null
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)

    fun registerRoutes(routing: Routing) {
        routing.route("/api/chat") {
            // Создать новую сессию
            post("/sessions") {
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
            get("/sessions") {
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
            get("/sessions/{sessionId}") {
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
            delete("/sessions/{sessionId}") {
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
            post("/sessions/{sessionId}/messages") {
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
                    
                    // Обрабатываем сообщение через GodAgentService (если доступен) или ReviewsChatService
                    val assistantMessage = if (godAgentService != null) {
                        logger.debug("Using GodAgentService for message processing")
                        val userId = "default" // TODO: добавить userId в SendMessageRequest
                        val response = kotlinx.coroutines.runBlocking {
                            godAgentService.processUserRequest(
                                message = request.message,
                                sessionId = sessionId,
                                userId = userId
                            )
                        }
                        // ChatResponse уже содержит ChatMessage
                        response.message
                    } else {
                        logger.debug("Using ReviewsChatService for message processing")
                        chatService.processMessage(
                            sessionId = sessionId,
                            userMessage = request.message
                        )
                    }
                    
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
            get("/sessions/{sessionId}/messages") {
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
            
            // Голосовой ввод
            post("/sessions/{sessionId}/voice") {
                if (speechRecognitionService == null || audioConversionService == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("Voice recognition is not available")
                    )
                    return@post
                }
                
                try {
                    val sessionId = call.parameters["sessionId"]
                        ?: throw IllegalArgumentException("sessionId is required")
                    
                    // Извлекаем аудио из multipart запроса
                    val audioData = extractAudio(call.receiveMultipart())
                    if (audioData == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("No audio data provided")
                        )
                        return@post
                    }
                    
                    // Конвертируем аудио в формат для Vosk
                    val converted = runCatching {
                        audioConversionService.convertToVoskWav(audioData)
                    }.getOrElse {
                        logger.error("Audio conversion failed", it)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Audio conversion failed: ${it.message}")
                        )
                        return@post
                    }
                    
                    // Распознаем речь
                    val recognizedText = runCatching {
                        speechRecognitionService.recognize(converted)
                    }.getOrElse {
                        logger.error("Speech recognition failed", it)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Speech recognition failed: ${it.message}")
                        )
                        return@post
                    }
                    
                    if (recognizedText.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Could not recognize speech")
                        )
                        return@post
                    }
                    
                    // Обрабатываем как обычное сообщение
                    val assistantMessage = chatService.processMessage(
                        sessionId = sessionId,
                        userMessage = recognizedText
                    )
                    
                    logger.info("Voice message processed: session=$sessionId, recognized='$recognizedText'")
                    
                    call.respond(
                        VoiceMessageResponse(
                            recognizedText = recognizedText,
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
                } catch (e: Exception) {
                    logger.error("Failed to process voice message", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to process voice message: ${e.message}")
                    )
                }
            }
        }
    }
    
    /**
     * Извлекает аудио данные из multipart запроса
     */
    private suspend fun extractAudio(multipart: io.ktor.http.content.MultiPartData): ByteArray? {
        var audioData: ByteArray? = null
        
        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    audioData = part.streamProvider().readBytes()
                }
                else -> {}
            }
            part.dispose()
        }
        
        return audioData
    }
}

/**
 * DTO для создания сессии
 */
@Serializable
data class CreateSessionRequest(
    val title: String? = null
)

/**
 * DTO для отправки сообщения
 */
@Serializable
data class SendMessageRequest(
    val message: String
)

/**
 * DTO для ответа с сообщением
 */
@Serializable
data class SendMessageResponse(
    val message: ChatMessageDto,
    val sessionId: String
)

/**
 * DTO для сессии чата
 */
@Serializable
data class ChatSessionDto(
    val id: String,
    val title: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * DTO для сообщения чата
 */
@Serializable
data class ChatMessageDto(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val citations: List<MessageCitationDto>,
    val createdAt: Long
)

/**
 * DTO для цитаты
 */
@Serializable
data class MessageCitationDto(
    val text: String,
    val documentPath: String,
    val documentTitle: String,
    val chunkId: String? = null
)

/**
 * DTO для истории чата
 */
@Serializable
data class ChatHistoryResponse(
    val sessionId: String,
    val messages: List<ChatMessageDto>
)

/**
 * DTO для ответа на голосовое сообщение
 */
@Serializable
data class VoiceMessageResponse(
    val recognizedText: String,
    val message: ChatMessageDto,
    val sessionId: String
)

/**
 * DTO для ошибки
 */
@Serializable
data class ErrorResponse(
    val message: String
)

