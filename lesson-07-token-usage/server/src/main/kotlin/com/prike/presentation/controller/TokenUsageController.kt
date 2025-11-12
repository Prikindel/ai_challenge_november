package com.prike.presentation.controller

import com.prike.domain.agent.TokenUsageAgent
import com.prike.domain.exception.AIServiceException
import com.prike.domain.exception.DomainException
import com.prike.domain.exception.ValidationException
import com.prike.presentation.dto.ErrorResponseDto
import com.prike.presentation.dto.TokenUsageAnalyzeRequestDto
import com.prike.presentation.dto.TokenUsageAnalyzeResponseDto
import com.prike.presentation.dto.TokenUsageHistoryResponseDto
import com.prike.presentation.dto.TokenUsageRunDto
import com.prike.presentation.dto.TokenUsageScenarioResultDto
import com.prike.presentation.dto.TokenUsageScenariosResponseDto
import com.prike.presentation.dto.TokenUsageScenarioTemplateDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory

class TokenUsageController(
    private val agent: TokenUsageAgent
) {

    private val logger = LoggerFactory.getLogger(TokenUsageController::class.java)
    private val instantFormatter = DateTimeFormatter.ISO_INSTANT

    fun configureRoutes(routing: Routing) {
        routing.route("/api/token-usage") {
            get("/scenarios") {
                call.respond(
                    HttpStatusCode.OK,
                    TokenUsageScenariosResponseDto(
                        scenarios = agent.getScenarioTemplates().map { it.toDto() },
                        promptTokenLimit = agent.getPromptTokenLimit(),
                        defaultMaxResponseTokens = agent.getDefaultMaxResponseTokens(),
                        tokenEncoding = agent.getTokenEncoding()
                    )
                )
            }

            get("/history") {
                val history = agent.getHistory().map { it.toDto() }
                call.respond(
                    HttpStatusCode.OK,
                    TokenUsageHistoryResponseDto(history = history)
                )
            }

            post("/analyze") {
                call.handleAnalyze()
            }
        }

        routing.get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
    }

    private suspend fun ApplicationCall.handleAnalyze() {
        try {
            val request = runCatching { receive<TokenUsageAnalyzeRequestDto>() }
                .getOrElse { TokenUsageAnalyzeRequestDto() }

            val command = TokenUsageAgent.AnalyzeTokenUsageCommand(
                scenarios = request.scenarios
                    ?.map { override ->
                        TokenUsageAgent.ScenarioOverride(
                            scenarioId = override.scenarioId,
                            promptText = override.promptText
                        )
                    }.orEmpty()
            )

            val currentRun = agent.analyze(command)
            val historyRuns = agent.getHistory()
                .filterNot { it.runId == currentRun.runId }
                .map { it.toDto() }

            respond(
                HttpStatusCode.OK,
                TokenUsageAnalyzeResponseDto(
                    currentRun = currentRun.toDto(),
                    history = historyRuns
                )
            )
        } catch (exception: DomainException) {
            val (status, error) = mapDomainError(exception)
            logger.warn("Domain error: ${exception.message}", exception)
            respond(status, error)
        } catch (exception: IllegalStateException) {
            logger.error("Configuration error: ${exception.message}", exception)
            respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(exception.message ?: "Ошибка конфигурации сервера")
            )
        } catch (exception: Exception) {
            logger.error("Unexpected error", exception)
            respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto("Внутренняя ошибка сервера: ${exception.message ?: "Неизвестная ошибка"}")
            )
        }
    }

    private fun mapDomainError(exception: DomainException): Pair<HttpStatusCode, ErrorResponseDto> = when (exception) {
        is ValidationException -> HttpStatusCode.BadRequest to ErrorResponseDto(exception.message ?: "Ошибка валидации")
        is AIServiceException -> HttpStatusCode.BadGateway to ErrorResponseDto(
            "Ошибка при обращении к AI сервису: ${exception.message}"
        )
        else -> HttpStatusCode.InternalServerError to ErrorResponseDto(
            "Внутренняя ошибка сервера: ${exception.message}"
        )
    }

    private fun TokenUsageAgent.ScenarioTemplate.toDto(): TokenUsageScenarioTemplateDto =
        TokenUsageScenarioTemplateDto(
            scenarioId = scenarioId,
            scenarioName = scenarioName,
            defaultPrompt = defaultPrompt,
            description = description
        )

    private fun TokenUsageAgent.TokenUsageRun.toDto(): TokenUsageRunDto =
        TokenUsageRunDto(
            runId = runId,
            startedAt = instantFormatter.format(startedAt),
            finishedAt = instantFormatter.format(finishedAt),
            results = results.map { it.toDto() }
        )

    private fun TokenUsageAgent.TokenUsageScenarioResult.toDto(): TokenUsageScenarioResultDto =
        TokenUsageScenarioResultDto(
            scenarioId = scenarioId,
            scenarioName = scenarioName,
            promptText = promptText,
            responseText = responseText,
            promptTokens = promptTokens,
            responseTokens = responseTokens,
            totalTokens = totalTokens,
            durationMs = durationMs,
            status = status.name.lowercase(),
            errorMessage = errorMessage
        )
}

