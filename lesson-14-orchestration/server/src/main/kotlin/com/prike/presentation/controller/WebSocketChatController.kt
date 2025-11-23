package com.prike.presentation.controller

import com.prike.domain.agent.OrchestrationAgent
import com.prike.presentation.dto.ChatMessageRequestDto
import com.prike.presentation.dto.FinalResponse
import com.prike.presentation.dto.StatusUpdate
import com.prike.presentation.dto.ToolCallInfoDto
import com.prike.presentation.dto.ToolCallUpdate
import com.prike.presentation.dto.WebSocketError
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Контроллер для WebSocket чата с LLM агентом
 * Отправляет промежуточные сообщения в реальном времени
 */
class WebSocketChatController(
    private val orchestrationAgent: OrchestrationAgent
) {
    private val logger = LoggerFactory.getLogger(WebSocketChatController::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    fun registerRoutes(routing: Routing) {
        routing.webSocket("/api/chat/ws") {
            try {
                logger.info("WebSocket connection established")
                
                // Ожидаем сообщение от клиента
                val message = incoming.receive() as? Frame.Text
                    ?: throw IllegalArgumentException("Expected text message")
                
                val request = json.decodeFromString<ChatMessageRequestDto>(message.readText())
                
                if (request.message.isBlank()) {
                    send(Frame.Text(json.encodeToString(WebSocketError("Сообщение не может быть пустым"))))
                    return@webSocket
                }
                
                logger.info("Обработка сообщения через WebSocket: ${request.message.take(100)}...")
                
                val startTime = System.currentTimeMillis()
                val toolCallsList = mutableListOf<ToolCallInfoDto>()
                
                // Сохраняем ссылку на WebSocket session для использования в callbacks
                val wsSession = this
                
                // Создаем канал для отправки сообщений из обработки в WebSocket контекст
                val messageChannel = Channel<String>(Channel.UNLIMITED)
                
                // Используем coroutineScope для создания дочерних корутин
                coroutineScope {
                    // Запускаем корутину для отправки сообщений из канала в WebSocket
                    // Это работает в контексте WebSocket session, поэтому сообщения отправляются немедленно
                    val senderJob = launch {
                        try {
                            for (messageJson in messageChannel) {
                                try {
                                    wsSession.send(Frame.Text(messageJson))
                                    logger.debug("Message sent to WebSocket: ${messageJson.take(100)}...")
                                } catch (e: Exception) {
                                    logger.error("Error sending message from channel: ${e.message}", e)
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Error in message sender: ${e.message}", e)
                        }
                    }
                    
                    // Сохраняем ссылку на scope для использования в callbacks
                    val wsScope = this@coroutineScope
                    
                    // Создаем канал для получения результата обработки
                    // Запускаем обработку в отдельной корутине, чтобы не блокировать WebSocket
                    val responseDeferred = async(Dispatchers.Default) {
                        try {
                            orchestrationAgent.processUserMessage(
                                userMessage = request.message,
                                statusCallback = { statusMessage ->
                                    // Отправляем промежуточное сообщение от LLM немедленно через канал
                                    // Используем launch в контексте WebSocket для немедленной отправки
                                    wsScope.launch {
                                        try {
                                            val statusUpdate = StatusUpdate(statusMessage)
                                            val messageJson = json.encodeToString(statusUpdate)
                                            messageChannel.send(messageJson) // Используем send вместо trySend для гарантированной отправки
                                            logger.debug("Status update sent to channel: ${statusMessage.take(50)}...")
                                        } catch (e: Exception) {
                                            logger.error("Error sending status update to channel: ${e.message}", e)
                                        }
                                    }
                                },
                                toolCallCallback = { toolName, status, message ->
                                    // Отправляем информацию о вызове инструмента немедленно через канал
                                    // Используем launch в контексте WebSocket для немедленной отправки
                                    wsScope.launch {
                                        try {
                                            val toolCallUpdate = ToolCallUpdate(toolName, status, message)
                                            val messageJson = json.encodeToString(toolCallUpdate)
                                            messageChannel.send(messageJson) // Используем send вместо trySend для гарантированной отправки
                                            logger.debug("Tool call update sent to channel: $toolName - $status")
                                            
                                            // Сохраняем информацию о вызове для финального ответа
                                            if (status == "success" || status == "error") {
                                                toolCallsList.add(
                                                    ToolCallInfoDto(
                                                        toolName = toolName,
                                                        serverId = null,
                                                        success = status == "success",
                                                        result = null
                                                    )
                                                )
                                            }
                                        } catch (e: Exception) {
                                            logger.error("Error sending tool call update to channel: ${e.message}", e)
                                        }
                                    }
                                }
                            )
                        } finally {
                            // Закрываем канал после завершения обработки
                            messageChannel.close()
                        }
                    }
                    
                    // Ждем результат обработки и завершения отправки всех сообщений из канала
                    val response = responseDeferred.await()
                    senderJob.join()
                    
                    val processingTime = System.currentTimeMillis() - startTime
                    
                    when (response) {
                        is OrchestrationAgent.AgentResponse.Success -> {
                            logger.info("Сообщение обработано успешно за ${processingTime}мс, вызвано инструментов: ${response.toolCalls.size}")
                            
                            // Отправляем финальный ответ
                            val finalResponse = FinalResponse(
                                response.message,
                                response.toolCalls.map { toolCall ->
                                    ToolCallInfoDto(
                                        toolName = toolCall.name,
                                        serverId = null,
                                        success = toolCall.success,
                                        result = if (toolCall.success) toolCall.result.take(200) else null
                                    )
                                },
                                processingTime
                            )
                            wsSession.send(Frame.Text(json.encodeToString(finalResponse)))
                            logger.info("Финальный ответ отправлен через WebSocket")
                        }
                        is OrchestrationAgent.AgentResponse.Error -> {
                            logger.error("Ошибка в чате: ${response.message}", response.cause)
                            wsSession.send(Frame.Text(json.encodeToString(WebSocketError(response.message))))
                        }
                    }
                }
                
            } catch (e: ClosedReceiveChannelException) {
                logger.info("WebSocket connection closed by client")
            } catch (e: Exception) {
                logger.error("Error in WebSocket chat: ${e.message}", e)
                try {
                    send(Frame.Text(json.encodeToString(WebSocketError("Ошибка обработки: ${e.message}"))))
                } catch (sendError: Exception) {
                    logger.error("Error sending error message: ${sendError.message}", sendError)
                }
            }
        }
    }
    
}

