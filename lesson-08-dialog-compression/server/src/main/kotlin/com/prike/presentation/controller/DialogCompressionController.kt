package com.prike.presentation.controller

import com.prike.config.DialogCompressionConfig
import com.prike.domain.agent.DialogCompressionAgent
import com.prike.domain.exception.AIServiceException
import com.prike.domain.exception.DomainException
import com.prike.domain.exception.ValidationException
import com.prike.domain.model.AgentResponseMetrics
import com.prike.domain.model.ComparisonReport
import com.prike.domain.model.ComparisonScenarioMetrics
import com.prike.domain.model.ContextRawMessage
import com.prike.domain.model.DialogMessage
import com.prike.domain.model.SummaryNode
import com.prike.presentation.dto.ComparisonRequestDto
import com.prike.presentation.dto.ComparisonResponseDto
import com.prike.presentation.dto.ComparisonScenariosResponseDto
import com.prike.presentation.dto.ContextRawMessageDto
import com.prike.presentation.dto.ContextUsageDto
import com.prike.presentation.dto.DialogStateResponseDto
import com.prike.presentation.dto.ScenarioInfoDto
import com.prike.presentation.dto.SendMessageRequestDto
import com.prike.presentation.dto.SendMessageResponseDto
import com.prike.presentation.dto.ScenarioMetricsDto
import com.prike.presentation.dto.StateMessageDto
import com.prike.presentation.dto.SummaryDto
import com.prike.presentation.dto.TokenUsageDto
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

class DialogCompressionController(
    private val agent: DialogCompressionAgent,
    private val config: DialogCompressionConfig
) {

    private val logger = LoggerFactory.getLogger(DialogCompressionController::class.java)
    private val formatter = DateTimeFormatter.ISO_INSTANT

    fun configureRoutes(routing: Routing) {
        routing.route("/api/dialog-compression") {
            post("/message") { call.handleMessage() }
            post("/reset") {
                agent.reset()
                call.respond(HttpStatusCode.OK, mapOf("status" to "reset"))
            }
            get("/state") { call.handleState() }
            get("/scenarios") { call.handleScenarios() }
            post("/comparison") { call.handleComparison() }
        }
    }

    private suspend fun ApplicationCall.handleMessage() {
        try {
            val request = receive<SendMessageRequestDto>()
            if (request.message.isBlank()) {
                throw ValidationException("Сообщение не должно быть пустым")
            }

            val result = agent.handleMessage(
                DialogCompressionAgent.HandleMessageCommand(
                    userMessage = request.message,
                    summaryIntervalOverride = request.summaryInterval,
                    maxSummariesInContext = request.maxSummariesInContext
                )
            )

            respond(HttpStatusCode.OK, result.toDto())
        } catch (exception: ValidationException) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to exception.message))
        } catch (exception: DomainException) {
            logger.warn("Domain error", exception)
            respond(HttpStatusCode.InternalServerError, mapOf("error" to exception.message))
        } catch (exception: AIServiceException) {
            logger.error("AI error", exception)
            respond(HttpStatusCode.BadGateway, mapOf("error" to exception.message))
        } catch (exception: Exception) {
            logger.error("Unexpected error", exception)
            respond(HttpStatusCode.InternalServerError, mapOf("error" to "Внутренняя ошибка сервера"))
        }
    }

    private suspend fun ApplicationCall.handleState() {
        val state = agent.getState()
        respond(
            HttpStatusCode.OK,
            DialogStateResponseDto(
                messages = state.messages.sortedBy { it.createdAt }.map { it.toDto() },
                summaries = state.summaries.sortedBy { it.createdAt }.map { it.toDto() }
            )
        )
    }

    private suspend fun ApplicationCall.handleScenarios() {
        respond(
            HttpStatusCode.OK,
            ComparisonScenariosResponseDto(
                scenarios = config.scenarios.map { scenario ->
                    ScenarioInfoDto(
                        id = scenario.id,
                        description = scenario.description,
                        messagesCount = scenario.seedMessages.size
                    )
                }
            )
        )
    }

    private suspend fun ApplicationCall.handleComparison() {
        try {
            val request = receive<ComparisonRequestDto>()
            val report = agent.runComparisonScenario(
                DialogCompressionAgent.RunComparisonScenarioCommand(request.scenarioId)
            )
            respond(HttpStatusCode.OK, report.toDto())
        } catch (exception: Exception) {
            logger.error("Comparison run failed", exception)
            respond(HttpStatusCode.InternalServerError, mapOf("error" to exception.message))
        }
    }

    private fun AgentResponseMetrics.toDto(): SendMessageResponseDto =
        SendMessageResponseDto(
            answer = answer,
            contextUsed = ContextUsageDto(
                summaryIds = contextUsed.summaryIds,
                rawMessages = contextUsed.rawMessages.map { it.toDto() }
            ),
            tokenUsage = TokenUsageDto(
                promptTokens = tokenUsage.promptTokens,
                completionTokens = tokenUsage.completionTokens,
                totalTokens = tokenUsage.totalTokens,
                hypotheticalPromptTokens = tokenUsage.hypotheticalPromptTokens,
                tokensSavedByCompression = tokenUsage.tokensSavedByCompression
            ),
            summaries = summaries.sortedBy { it.createdAt }.map { it.toDto() },
            rawMessagesCount = rawMessagesCount,
            summaryInterval = summaryInterval
        )

    private fun ContextRawMessage.toDto(): ContextRawMessageDto =
        ContextRawMessageDto(
            id = id,
            role = role.name.lowercase(),
            preview = contentPreview,
            createdAt = formatter.format(createdAt)
        )

    private fun SummaryNode.toDto(): SummaryDto =
        SummaryDto(
            id = id,
            createdAt = formatter.format(createdAt),
            summary = summary,
            facts = facts,
            openQuestions = openQuestions,
            sourceMessageIds = sourceMessageIds,
            anchorMessageId = anchorMessageId
        )

    private fun DialogMessage.toDto(): StateMessageDto =
        StateMessageDto(
            id = id,
            role = role.name.lowercase(),
            content = content,
            createdAt = formatter.format(createdAt),
            summarized = summarized
        )

    private fun ComparisonReport.toDto(): ComparisonResponseDto =
        ComparisonResponseDto(
            scenarioId = scenarioId,
            description = description,
            withCompressionMetrics = withCompressionMetrics.toDto(),
            withoutCompressionMetrics = withoutCompressionMetrics.toDto(),
            analysisText = analysisText
        )

    private fun ComparisonScenarioMetrics.toDto(): ScenarioMetricsDto =
        ScenarioMetricsDto(
            totalPromptTokens = totalPromptTokens,
            totalCompletionTokens = totalCompletionTokens,
            totalTokens = totalTokens,
            durationMs = durationMs,
            messagesProcessed = messagesProcessed,
            summariesGenerated = summariesGenerated,
            tokensSaved = tokensSaved,
            qualityNotes = qualityNotes
        )
}
