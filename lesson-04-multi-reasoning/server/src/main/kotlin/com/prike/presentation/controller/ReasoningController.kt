package com.prike.presentation.controller

import com.prike.domain.agent.ReasoningAgent
import com.prike.domain.exception.AIServiceException
import com.prike.presentation.dto.DebugInfoDto
import com.prike.presentation.dto.ExpertPanelResponseDto
import com.prike.presentation.dto.ExpertResponseDto
import com.prike.presentation.dto.PromptFromOtherAIResponseDto
import com.prike.presentation.dto.ReasoningDebugDto
import com.prike.presentation.dto.ReasoningDefaultTaskResponseDto
import com.prike.presentation.dto.ReasoningModeResponseDto
import com.prike.presentation.dto.ReasoningRequestDto
import com.prike.presentation.dto.ReasoningResponseDto
import com.prike.presentation.dto.ErrorResponseDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

class ReasoningController(
    private val reasoningAgent: ReasoningAgent?
) {
    private val logger = LoggerFactory.getLogger(ReasoningController::class.java)

    fun configureRoutes(routing: Routing) {
        routing.post("/reasoning") {
            call.handleReasoningRequest()
        }

        routing.get("/reasoning/default") {
            if (reasoningAgent == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponseDto("AI агент недоступен. Проверьте конфигурацию OpenAI.")
                )
            } else {
                call.respond(
                    HttpStatusCode.OK,
                    ReasoningDefaultTaskResponseDto(defaultTask = reasoningAgent.getDefaultTask())
                )
            }
        }

        routing.get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
    }

    private suspend fun ApplicationCall.handleReasoningRequest() {
        if (reasoningAgent == null) {
            respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponseDto("AI агент недоступен. Проверьте конфигурацию OpenAI.")
            )
            return
        }

        val request = runCatching {
            receiveNullable<ReasoningRequestDto>()
        }.onFailure { throwable ->
            logger.warn("Не удалось распарсить тело запроса: ${throwable.message}")
        }.getOrNull()

        try {
            val mode = ReasoningAgent.ReasoningMode.fromString(request?.mode)
            val result = reasoningAgent.solve(request?.question, mode)
            val response = ReasoningResponseDto(
                task = result.task,
                mode = result.mode.name.lowercase(),
                direct = result.direct?.let {
                    ReasoningModeResponseDto(
                        prompt = it.prompt,
                        answer = it.answer,
                        debug = it.debug.toDto()
                    )
                },
                stepByStep = result.stepByStep?.let {
                    ReasoningModeResponseDto(
                        prompt = it.prompt,
                        answer = it.answer,
                        debug = it.debug.toDto()
                    )
                },
                promptFromOtherAI = result.promptFromOtherAI?.let {
                    PromptFromOtherAIResponseDto(
                        generatedPrompt = it.generatedPrompt,
                        answer = it.answer,
                        notes = it.notes,
                        usedFallback = it.usedFallback,
                        promptDebug = it.promptDebug.toDto(),
                        answerDebug = it.answerDebug.toDto()
                    )
                },
                expertPanel = result.expertPanel?.let { panel ->
                    ExpertPanelResponseDto(
                        experts = panel.experts.map { expert ->
                            ExpertResponseDto(
                                name = expert.name,
                                style = expert.style,
                                answer = expert.answer,
                                reasoning = expert.reasoning,
                                debug = expert.debug.toDto()
                            )
                        },
                        summary = panel.summary,
                        summaryDebug = panel.summaryDebug.toDto()
                    )
                },
                comparison = result.comparison,
                debug = result.comparisonDebug?.let {
                    ReasoningDebugDto(comparison = it.toDto())
                }
            )
            respond(HttpStatusCode.OK, response)
        } catch (e: AIServiceException) {
            logger.error("Ошибка при работе с AI сервисом", e)
            respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto("Ошибка при обращении к AI сервису: ${e.message}")
            )
        } catch (e: Exception) {
            logger.error("Неожиданная ошибка обработки reasoning запроса", e)
            respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto("Внутренняя ошибка сервера: ${e.message ?: "Неизвестная ошибка"}")
            )
        }
    }

    private fun ReasoningAgent.DebugInfo.toDto() = DebugInfoDto(
        llmRequest = llmRequest,
        llmResponse = llmResponse
    )
}


