package com.prike.presentation.controller

import com.prike.domain.agent.MemoryOrchestrator
import com.prike.presentation.dto.MemoryDtos
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

/**
 * Контроллер для API работы с внешней памятью
 * 
 * Endpoints:
 * - POST /api/memory/message - отправка сообщения (с сохранением)
 * - GET /api/memory/history - получение истории
 * - POST /api/memory/reset - сброс памяти
 * - GET /api/memory/stats - статистика памяти
 */
class MemoryController(
    private val orchestrator: MemoryOrchestrator
) {
    private val logger = LoggerFactory.getLogger(MemoryController::class.java)
    
    fun configureRoutes(routing: Routing) {
        routing.route("/api/memory") {
            // Отправка сообщения
            post("/message") {
                try {
                    val request = call.receive<MemoryDtos.SendMessageRequest>()
                    
                    if (request.message.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            MemoryDtos.ErrorResponse("Сообщение не может быть пустым")
                        )
                        return@post
                    }
                    
                    val response = orchestrator.handleMessage(request.message)
                    
                    call.respond(
                        HttpStatusCode.OK,
                        MemoryDtos.SendMessageResponse(
                            message = response.message,
                            usage = response.usage?.let {
                                MemoryDtos.UsageDto(
                                    promptTokens = it.promptTokens,
                                    completionTokens = it.completionTokens,
                                    totalTokens = it.totalTokens
                                )
                            }
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Ошибка при обработке сообщения", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        MemoryDtos.ErrorResponse("Ошибка при обработке сообщения: ${e.message}")
                    )
                }
            }
            
            // Получение истории
            get("/history") {
                try {
                    val history = orchestrator.getHistory()
                    val historyDtos = history.map { entry ->
                        MemoryDtos.HistoryEntryDto(
                            id = entry.id,
                            role = entry.role.name.lowercase(),
                            content = entry.content,
                            timestamp = entry.timestamp,
                            metadata = entry.metadata?.let {
                                MemoryDtos.MemoryMetadataDto(
                                    model = it.model,
                                    promptTokens = it.promptTokens,
                                    completionTokens = it.completionTokens,
                                    totalTokens = it.totalTokens
                                )
                            }
                        )
                    }
                    
                    call.respond(
                        HttpStatusCode.OK,
                        MemoryDtos.HistoryResponse(history = historyDtos)
                    )
                } catch (e: Exception) {
                    logger.error("Ошибка при получении истории", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        MemoryDtos.ErrorResponse("Ошибка при получении истории: ${e.message}")
                    )
                }
            }
            
            // Сброс памяти
            post("/reset") {
                try {
                    orchestrator.reset()
                    call.respond(
                        HttpStatusCode.OK,
                        MemoryDtos.SuccessResponse(message = "Память успешно сброшена")
                    )
                } catch (e: Exception) {
                    logger.error("Ошибка при сбросе памяти", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        MemoryDtos.ErrorResponse("Ошибка при сбросе памяти: ${e.message}")
                    )
                }
            }
            
            // Статистика памяти
            get("/stats") {
                try {
                    val statsResult = orchestrator.getStats()
                    statsResult.fold(
                        onSuccess = { stats ->
                            call.respond(
                                HttpStatusCode.OK,
                                MemoryDtos.StatsResponse(
                                    totalEntries = stats.totalEntries,
                                    userMessages = stats.userMessages,
                                    assistantMessages = stats.assistantMessages,
                                    oldestEntry = stats.oldestEntry?.toEpochMilli(),
                                    newestEntry = stats.newestEntry?.toEpochMilli()
                                )
                            )
                        },
                        onFailure = { error ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                MemoryDtos.ErrorResponse("Ошибка при получении статистики: ${error.message}")
                            )
                        }
                    )
                } catch (e: Exception) {
                    logger.error("Ошибка при получении статистики", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        MemoryDtos.ErrorResponse("Ошибка при получении статистики: ${e.message}")
                    )
                }
            }
        }
    }
}

