package com.prike.presentation.controller

import com.prike.domain.agent.OrchestrationAgent
import com.prike.presentation.dto.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * WebSocket контроллер для чата с LLM агентом
 * Отправляет статусы и обновления в реальном времени
 */
class WebSocketChatController(
    private val orchestrationAgent: OrchestrationAgent
) {
    private val logger = LoggerFactory.getLogger(WebSocketChatController::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    
    fun registerRoutes(routing: Routing) {
        routing.webSocket("/api/chat/ws") {
            val messageChannel = Channel<String>(Channel.UNLIMITED)
            var sessionActive = true
            
            coroutineScope {
                // Запускаем корутину-отправитель
                val senderJob = launch {
                    try {
                        messageChannel.consumeEach { messageJson ->
                            if (sessionActive) {
                                try {
                                    outgoing.send(Frame.Text(messageJson))
                                } catch (e: Exception) {
                                    logger.error("Ошибка отправки WebSocket сообщения: ${e.message}", e)
                                    sessionActive = false
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Ошибка в корутине-отправителе: ${e.message}", e)
                    }
                }
                
                try {
                    // Обрабатываем входящие сообщения
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            logger.debug("Получено WebSocket сообщение: $text")
                            
                            // Парсим запрос
                            val request = try {
                                json.decodeFromString<ChatMessageRequestDto>(text)
                            } catch (e: Exception) {
                                logger.error("Ошибка парсинга запроса: ${e.message}", e)
                                val errorResponse = json.encodeToString(
                                    ErrorDto(message = "Неверный формат запроса: ${e.message}")
                                )
                                messageChannel.trySend(errorResponse)
                                continue
                            }
                            
                            if (request.message.isBlank()) {
                                val errorResponse = json.encodeToString(
                                    ErrorDto(message = "Сообщение не может быть пустым")
                                )
                                messageChannel.trySend(errorResponse)
                                continue
                            }
                            
                            logger.info("Обработка сообщения пользователя через WebSocket: ${request.message.take(100)}...")
                            
                            // Отправляем начальное сообщение "Думаю..."
                            val initialStatus = StatusUpdate(message = "Думаю...")
                            messageChannel.trySend(json.encodeToString(initialStatus))
                            
                            // Callbacks для отправки статусов
                            val statusCallback: (String) -> Unit = { statusMessage ->
                                val statusUpdate = StatusUpdate(message = statusMessage)
                                val messageJson = json.encodeToString(statusUpdate)
                                messageChannel.trySend(messageJson)
                            }
                            
                            val toolCallCallback: (String, String, String?) -> Unit = { toolName, status, message ->
                                val toolCallUpdate = ToolCallUpdate(
                                    toolName = toolName,
                                    status = status,
                                    message = message
                                )
                                val messageJson = json.encodeToString(toolCallUpdate)
                                messageChannel.trySend(messageJson)
                            }
                            
                            val startTime = System.currentTimeMillis()
                            
                            // Обрабатываем сообщение с callbacks
                            try {
                                val response = orchestrationAgent.processUserMessage(
                                    userMessage = request.message,
                                    statusCallback = statusCallback,
                                    toolCallCallback = toolCallCallback
                                )
                                
                                val processingTime = System.currentTimeMillis() - startTime
                                
                                when (response) {
                                    is OrchestrationAgent.AgentResponse.Success -> {
                                        logger.info("Сообщение обработано успешно за ${processingTime}мс, вызвано инструментов: ${response.toolCalls.size}")
                                        
                                        // Находим serverId для каждого инструмента
                                        val toolCallsWithServer = response.toolCalls.map { toolCall ->
                                            ToolCallInfoDto(
                                                toolName = toolCall.name,
                                                serverId = null,
                                                success = toolCall.success,
                                                result = if (toolCall.success) toolCall.result.take(200) else null
                                            )
                                        }
                                        
                                        // Отправляем финальный ответ
                                        val finalResponse = FinalResponse(
                                            message = response.message,
                                            toolCalls = toolCallsWithServer,
                                            processingTime = processingTime
                                        )
                                        val finalMessageJson = json.encodeToString(finalResponse)
                                        messageChannel.trySend(finalMessageJson)
                                    }
                                    is OrchestrationAgent.AgentResponse.Error -> {
                                        logger.error("Ошибка в чате: ${response.message}", response.cause)
                                        val errorResponse = json.encodeToString(
                                            ErrorDto(message = response.message)
                                        )
                                        messageChannel.trySend(errorResponse)
                                    }
                                }
                            } catch (e: Exception) {
                                logger.error("Ошибка обработки сообщения: ${e.message}", e)
                                val errorResponse = json.encodeToString(
                                    ErrorDto(message = "Ошибка обработки: ${e.message}")
                                )
                                messageChannel.trySend(errorResponse)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Ошибка WebSocket соединения: ${e.message}", e)
                } finally {
                    sessionActive = false
                    senderJob.cancel()
                    messageChannel.close()
                }
            }
        }
    }
}

