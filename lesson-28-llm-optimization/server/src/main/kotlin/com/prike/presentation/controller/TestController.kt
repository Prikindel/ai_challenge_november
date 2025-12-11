package com.prike.presentation.controller

import com.prike.domain.model.LLMParameters
import com.prike.domain.repository.TestRepository
import com.prike.domain.service.LLMTestService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.prike.presentation.dto.ErrorResponse
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Контроллер для API тестирования LLM
 */
class TestController(
    private val testService: LLMTestService,
    private val testRepository: TestRepository
) {
    private val logger = LoggerFactory.getLogger(TestController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Получение списка доступных тестов
            get("/api/test/tests") {
                try {
                    val tests = testRepository.getAllTests()
                    call.respond(tests.map { test ->
                        TestDto(
                            id = test.id,
                            name = test.name,
                            questionCount = test.questions.size
                        )
                    })
                } catch (e: Exception) {
                    logger.error("Failed to get tests", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get tests: ${e.message}")
                    )
                }
            }
            
            // Запуск теста с указанной конфигурацией
            post("/api/test/run") {
                try {
                    val request = call.receive<TestRunRequest>()
                    
                    // Валидация
                    if (request.testId.isBlank()) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("testId cannot be blank")
                        )
                        return@post
                    }
                    
                    val test = testRepository.getTest(request.testId)
                        ?: throw IllegalArgumentException("Test not found: ${request.testId}")
                    
                    // Парсим параметры из запроса
                    val parameters = request.parameters?.let { params ->
                        LLMParameters(
                            temperature = params.temperature ?: 0.7,
                            maxTokens = params.maxTokens ?: 2048,
                            topP = params.topP ?: 0.9,
                            topK = params.topK ?: 40,
                            repeatPenalty = params.repeatPenalty ?: 1.1,
                            contextWindow = params.contextWindow ?: 4096,
                            seed = params.seed
                        )
                    } ?: LLMParameters()
                    
                    val templateId = request.templateId ?: "default"
                    
                    logger.debug("Test run request: testId=${request.testId}, templateId=$templateId, parameters=$parameters")
                    
                    // Запускаем тест
                    val result = testService.runTest(
                        test = test,
                        parameters = parameters,
                        templateId = templateId
                    )
                    
                    call.respond(result)
                } catch (e: IllegalArgumentException) {
                    logger.error("Invalid test request", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid request: ${e.message}")
                    )
                } catch (e: Exception) {
                    logger.error("Test run error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to run test: ${e.message}")
                    )
                }
            }
        }
    }
}

@Serializable
data class TestDto(
    val id: String,
    val name: String,
    val questionCount: Int
)

@Serializable
data class TestRunRequest(
    val testId: String,
    val parameters: LLMParametersDto? = null,
    val templateId: String? = null
)

@Serializable
data class LLMParametersDto(
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val repeatPenalty: Double? = null,
    val contextWindow: Int? = null,
    val seed: Int? = null
)

