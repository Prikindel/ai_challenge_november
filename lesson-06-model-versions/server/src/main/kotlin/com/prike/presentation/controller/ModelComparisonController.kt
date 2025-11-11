package com.prike.presentation.controller

import com.prike.config.ModelComparisonLessonConfig
import com.prike.domain.agent.ModelComparisonAgent
import com.prike.domain.exception.AIServiceException
import com.prike.domain.exception.DomainException
import com.prike.domain.exception.ValidationException
import com.prike.presentation.dto.ErrorResponseDto
import com.prike.presentation.dto.ModelComparisonRequestDto
import com.prike.presentation.dto.ModelComparisonResponseDto
import com.prike.presentation.dto.ModelInfoDto
import com.prike.presentation.dto.ModelLinkDto
import com.prike.presentation.dto.ModelMetaDto
import com.prike.presentation.dto.ModelResultDto
import com.prike.presentation.dto.ModelsCatalogDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

class ModelComparisonController(
    private val agent: ModelComparisonAgent,
    private val lessonConfig: ModelComparisonLessonConfig
) {

    private val logger = LoggerFactory.getLogger(ModelComparisonController::class.java)

    fun configureRoutes(routing: Routing) {
        routing.route("/api/models") {
            get {
                call.respond(
                    HttpStatusCode.OK,
                    ModelsCatalogDto(
                        defaultQuestion = lessonConfig.defaultQuestion,
                        defaultModelIds = lessonConfig.defaultModelIds,
                        models = lessonConfig.models.map { model ->
                            ModelInfoDto(
                                id = model.id,
                                displayName = model.displayName,
                                endpoint = model.endpoint,
                                huggingFaceUrl = model.huggingFaceUrl,
                                pricePer1kTokensUsd = model.pricePer1kTokensUsd,
                                defaultParams = model.defaultParams
                                    .mapValues { entry -> entry.value?.toString() }
                            )
                        }
                    )
                )
            }
            post("/compare") {
                call.handleCompareRequest()
            }
        }

        routing.get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
    }

    private suspend fun ApplicationCall.handleCompareRequest() {
        try {
            val request = receive<ModelComparisonRequestDto>()
            val result = agent.compare(
                requestedQuestion = request.question,
                requestedModelIds = request.modelIds,
                includeComparison = request.includeComparison ?: true
            )
            respond(
                HttpStatusCode.OK,
                result.toDto()
            )
        } catch (exception: DomainException) {
            val (status, error) = mapError(exception)
            logger.warn("Domain error: ${exception.message}", exception)
            respond(status, error)
        } catch (exception: Exception) {
            logger.error("Unexpected error", exception)
            respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto("Внутренняя ошибка сервера: ${exception.message ?: "Неизвестная ошибка"}")
            )
        }
    }

    private fun mapError(exception: DomainException): Pair<HttpStatusCode, ErrorResponseDto> = when (exception) {
        is ValidationException -> HttpStatusCode.BadRequest to ErrorResponseDto(exception.message ?: "Ошибка валидации")
        is AIServiceException -> HttpStatusCode.BadGateway to ErrorResponseDto(
            "Ошибка при обращении к AI сервису: ${exception.message}"
        )
        else -> HttpStatusCode.InternalServerError to ErrorResponseDto(
            "Внутренняя ошибка сервера: ${exception.message}"
        )
    }

    private fun ModelComparisonAgent.Result.toDto(): ModelComparisonResponseDto =
        ModelComparisonResponseDto(
            defaultQuestion = defaultQuestion,
            defaultModelIds = defaultModelIds,
            question = question,
            modelResults = modelResults.map { it.toDto() },
            comparisonSummary = comparisonSummary,
            modelLinks = modelLinks.map { it.toDto() },
            comparisonEnabled = comparisonEnabled
        )

    private fun ModelComparisonAgent.ModelRun.toDto(): ModelResultDto =
        ModelResultDto(
            modelId = modelId,
            displayName = displayName,
            huggingFaceUrl = huggingFaceUrl,
            answer = answer,
            isError = isError,
            meta = ModelMetaDto(
                durationMs = meta.durationMs,
                promptTokens = meta.promptTokens,
                completionTokens = meta.completionTokens,
                totalTokens = meta.totalTokens,
                costUsd = meta.costUsd
            )
        )

    private fun ModelComparisonAgent.ModelLink.toDto(): ModelLinkDto =
        ModelLinkDto(
            modelId = modelId,
            huggingFaceUrl = huggingFaceUrl
        )
}

