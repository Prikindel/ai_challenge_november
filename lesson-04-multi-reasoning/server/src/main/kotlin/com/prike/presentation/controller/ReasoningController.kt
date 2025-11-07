package com.prike.presentation.controller

import com.prike.domain.agent.ReasoningAgent
import com.prike.domain.exception.AIServiceException
import com.prike.presentation.dto.DebugInfoDto
import com.prike.presentation.dto.ExpertPanelResponseDto
import com.prike.presentation.dto.ExpertResponseDto
import com.prike.presentation.dto.PromptFromOtherAIResponseDto
import com.prike.presentation.dto.ReasoningDebugDto
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

        runCatching {
            receiveNullable<ReasoningRequestDto>()
        }.onFailure { throwable ->
            logger.warn("Не удалось распарсить тело запроса: ${throwable.message}")
        }

        try {
            val result = reasoningAgent.solve()
            val response = ReasoningResponseDto(
                task = result.task,
                direct = ReasoningModeResponseDto(
                    prompt = result.direct.prompt,
                    answer = result.direct.answer,
                    debug = result.direct.debug.toDto()
                ),
                stepByStep = ReasoningModeResponseDto(
                    prompt = result.stepByStep.prompt,
                    answer = result.stepByStep.answer,
                    debug = result.stepByStep.debug.toDto()
                ),
                promptFromOtherAI = PromptFromOtherAIResponseDto(
                    generatedPrompt = result.promptFromOtherAI.generatedPrompt,
                    answer = result.promptFromOtherAI.answer,
                    promptDebug = result.promptFromOtherAI.promptDebug.toDto(),
                    answerDebug = result.promptFromOtherAI.answerDebug.toDto()
                ),
                expertPanel = ExpertPanelResponseDto(
                    experts = result.expertPanel.experts.map { expert ->
                        ExpertResponseDto(
                            name = expert.name,
                            style = expert.style,
                            answer = expert.answer,
                            reasoning = expert.reasoning,
                            debug = expert.debug.toDto()
                        )
                    },
                    summary = result.expertPanel.summary,
                    summaryDebug = result.expertPanel.summaryDebug.toDto()
                ),
                comparison = result.comparison,
                debug = ReasoningDebugDto(
                    comparison = result.comparisonDebug.toDto()
                )
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


