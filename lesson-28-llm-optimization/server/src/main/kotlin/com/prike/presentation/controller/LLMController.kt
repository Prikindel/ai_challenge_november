package com.prike.presentation.controller

import com.prike.domain.service.LLMService
import com.prike.domain.service.PromptTemplateService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.prike.presentation.dto.ErrorResponse
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Контроллер для API работы с LLM
 */
class LLMController(
    private val llmService: LLMService,
    private val promptTemplateService: PromptTemplateService? = null
) {
    private val logger = LoggerFactory.getLogger(LLMController::class.java)
    
    fun registerRoutes(routing: Routing) {
        routing.apply {
            // Обычный запрос к LLM (без контекста)
            post("/api/llm/chat") {
                try {
                    val request = call.receive<LLMChatRequest>()
                    
                    // Валидация входных данных
                    if (request.question.isBlank()) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.BadRequest,
                            ErrorResponse("question cannot be blank")
                        )
                        return@post
                    }
                    
                    // Используем новый метод generateResponse с поддержкой шаблонов
                    val response = if (request.templateId != null || request.context != null) {
                        llmService.generateResponse(
                            userMessage = request.question,
                            context = request.context,
                            templateId = request.templateId
                        )
                    } else {
                        // Fallback на старый метод для обратной совместимости
                        llmService.generateAnswer(
                            question = request.question,
                            systemPrompt = request.systemPrompt
                        )
                    }
                    
                    call.respond(LLMChatResponse(
                        question = request.question,
                        answer = response.answer,
                        tokensUsed = response.tokensUsed
                    ))
                } catch (e: Exception) {
                    logger.error("LLM chat error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get answer from LLM: ${e.message}")
                    )
                }
            }
            
            // Получение списка доступных шаблонов промптов
            get("/api/prompt-templates") {
                try {
                    val templates = promptTemplateService?.getAllTemplates() ?: emptyList()
                    call.respond(templates.map { template ->
                        PromptTemplateDto(
                            id = template.id,
                            name = template.name,
                            description = template.description
                        )
                    })
                } catch (e: Exception) {
                    logger.error("Failed to get prompt templates", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get prompt templates: ${e.message}")
                    )
                }
            }
            
            // Получение статуса LLM
            get("/api/llm/status") {
                try {
                    val localLLMConfig = llmService.getLocalLLMConfig()
                    val isLocalEnabled = localLLMConfig?.enabled == true
                    val isLocalAvailable = if (isLocalEnabled) {
                        kotlinx.coroutines.runBlocking {
                            llmService.checkLocalLLMAvailability()
                        }
                    } else {
                        false
                    }
                    
                    call.respond(LLMStatusResponse(
                        provider = llmService.getProviderInfo(),
                        localLLM = if (localLLMConfig != null) {
                            LocalLLMStatus(
                                enabled = localLLMConfig.enabled,
                                provider = localLLMConfig.provider,
                                baseUrl = localLLMConfig.baseUrl,
                                model = localLLMConfig.model,
                                available = isLocalAvailable
                            )
                        } else {
                            null
                        }
                    ))
                } catch (e: Exception) {
                    logger.error("LLM status error", e)
                    call.respond(
                        io.ktor.http.HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to get LLM status: ${e.message}")
                    )
                }
            }
        }
    }
}

@Serializable
data class LLMChatRequest(
    val question: String,
    val systemPrompt: String? = null,
    val templateId: String? = null,
    val context: String? = null
)

@Serializable
data class LLMChatResponse(
    val question: String,
    val answer: String,
    val tokensUsed: Int
)

@Serializable
data class LLMStatusResponse(
    val provider: String,
    val localLLM: LocalLLMStatus?
)

@Serializable
data class LocalLLMStatus(
    val enabled: Boolean,
    val provider: String,
    val baseUrl: String,
    val model: String,
    val available: Boolean
)

@Serializable
data class PromptTemplateDto(
    val id: String,
    val name: String,
    val description: String
)

