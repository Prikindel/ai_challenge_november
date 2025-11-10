package com.prike.presentation.controller

import com.prike.domain.agent.TemperatureAgent
import com.prike.domain.exception.AIServiceException
import com.prike.domain.exception.DomainException
import com.prike.domain.exception.ValidationException
import com.prike.presentation.dto.ErrorResponseDto
import com.prike.presentation.dto.TemperatureDefaultsDto
import com.prike.presentation.dto.TemperatureMetaDto
import com.prike.presentation.dto.TemperatureRecommendationDto
import com.prike.presentation.dto.TemperatureRequestDto
import com.prike.presentation.dto.TemperatureResponseDto
import com.prike.presentation.dto.TemperatureResultDto
import com.prike.presentation.dto.TemperatureComparisonDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

class TemperatureController(
    private val agent: TemperatureAgent
) {

    private val logger = LoggerFactory.getLogger(TemperatureController::class.java)

    fun configureRoutes(routing: Routing) {
        routing.route("/temperature") {
            get {
                call.respond(
                    HttpStatusCode.OK,
                    TemperatureDefaultsDto(
                        defaultQuestion = agent.getDefaultQuestion(),
                        defaultTemperatures = agent.getDefaultTemperatures()
                    )
                )
            }
            post {
                call.handleAnalyzeRequest()
            }
        }

        routing.get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
    }

    private suspend fun ApplicationCall.handleAnalyzeRequest() {
        try {
            val request = receive<TemperatureRequestDto>()
            val result = agent.analyze(request.question, request.temperatures)
            respond(
                HttpStatusCode.OK,
                result.toDto()
            )
        } catch (exception: DomainException) {
            logger.warn("Domain error: ${exception.message}", exception)
            val (status, error) = mapError(exception)
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

    private fun TemperatureAgent.Result.toDto(): TemperatureResponseDto =
        TemperatureResponseDto(
            defaultQuestion = defaultQuestion,
            question = question,
            results = results.map { it.toDto() },
            comparison = comparison.toDto()
        )

    private fun TemperatureAgent.TemperatureRun.toDto(): TemperatureResultDto =
        TemperatureResultDto(
            mode = mode,
            temperature = temperature,
            answer = answer,
            meta = TemperatureMetaDto(
                durationMs = meta.durationMs,
                promptTokens = meta.promptTokens,
                completionTokens = meta.completionTokens,
                totalTokens = meta.totalTokens,
                requestJson = meta.requestJson,
                responseJson = meta.responseJson
            )
        )

    private fun TemperatureAgent.ComparisonResult.toDto(): TemperatureComparisonDto =
        TemperatureComparisonDto(
            summary = summary,
            perTemperature = perTemperature.map { it.toDto() }
        )

    private fun TemperatureAgent.TemperatureRecommendation.toDto(): TemperatureRecommendationDto =
        TemperatureRecommendationDto(
            temperature = temperature,
            mode = mode,
            accuracy = accuracy,
            creativity = creativity,
            diversity = diversity,
            recommendation = recommendation
        )
}

