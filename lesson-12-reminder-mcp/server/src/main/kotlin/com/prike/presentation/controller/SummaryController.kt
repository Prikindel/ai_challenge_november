package com.prike.presentation.controller

import com.prike.data.repository.SummaryRepository
import com.prike.domain.service.SchedulerService
import com.prike.presentation.dto.SchedulerStatusResponse
import com.prike.presentation.dto.SchedulerStatusDto
import com.prike.presentation.dto.SummariesResponse
import com.prike.presentation.dto.SummaryDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Контроллер для работы с summary
 */
class SummaryController(
    private val summaryRepository: SummaryRepository,
    private val schedulerService: SchedulerService
) {
    fun registerRoutes(routing: Routing) {
        routing.route("/api/summaries") {
            // Получить список summary
            get {
                try {
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    val result = summaryRepository.getAll(limit)
                    
                    when {
                        result.isSuccess -> {
                            val summaries = result.getOrNull() ?: emptyList()
                            call.respond(
                                HttpStatusCode.OK,
                                SummariesResponse(
                                    success = true,
                                    summaries = summaries.map { summary ->
                                        SummaryDto(
                                            id = summary.id,
                                            source = summary.source,
                                            periodStart = summary.periodStart,
                                            periodEnd = summary.periodEnd,
                                            summaryText = summary.summaryText,
                                            messageCount = summary.messageCount,
                                            generatedAt = summary.generatedAt,
                                            deliveredToTelegram = summary.deliveredToTelegram,
                                            llmModel = summary.llmModel
                                        )
                                    },
                                    total = summaries.size
                                )
                            )
                        }
                        else -> {
                            val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                SummariesResponse(
                                    success = false,
                                    error = errorMessage
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    val errorMessage = e.message ?: "Unknown error"
                    call.respond(
                        HttpStatusCode.BadRequest,
                        SummariesResponse(
                            success = false,
                            error = errorMessage
                        )
                    )
                }
            }
        }
        
        routing.route("/api/scheduler") {
            // Получить статус планировщика
            get {
                try {
                    val status = schedulerService.getStatus()
                    call.respond(
                        HttpStatusCode.OK,
                        SchedulerStatusResponse(
                            success = true,
                            status = SchedulerStatusDto(
                                isRunning = status.isRunning,
                                enabled = status.enabled,
                                intervalMinutes = status.intervalMinutes,
                                periodHours = status.periodHours,
                                activeSource = status.activeSource
                            )
                        )
                    )
                } catch (e: Exception) {
                    val errorMessage = e.message ?: "Unknown error"
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        SchedulerStatusResponse(
                            success = false,
                            error = errorMessage
                        )
                    )
                }
            }
        }
    }
}

