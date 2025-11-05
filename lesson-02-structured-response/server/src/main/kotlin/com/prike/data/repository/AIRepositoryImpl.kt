package com.prike.data.repository

import com.prike.data.client.OpenAIClient
import com.prike.data.dto.AnimalEncyclopediaResponse
import com.prike.data.dto.TopicValidationError
import com.prike.data.dto.TopicValidationErrorCode
import com.prike.domain.exception.AIServiceException
import com.prike.domain.repository.AIRepository
import com.prike.presentation.dto.ChatResponseResult
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Реализация репозитория AI
 */
class AIRepositoryImpl(
    private val aiClient: OpenAIClient
) : AIRepository {
    private val logger = LoggerFactory.getLogger(AIRepositoryImpl::class.java)
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override suspend fun getStructuredAIResponse(userMessage: String): ChatResponseResult {
        return try {
            val completionResult = aiClient.getCompletion(userMessage)
            val rawContent = completionResult.response.choices.firstOrNull()?.message?.content
                ?: throw AIServiceException("Пустой ответ от AI API (choices пусты)")
            
            val content = rawContent.trim()
            if (content.isBlank()) {
                // Возвращаем ошибку валидации темы, если ответ пустой
                val errorResponse = AnimalEncyclopediaResponse.Error(
                    error = TopicValidationError(
                        errorCode = TopicValidationErrorCode.TOPIC_MISMATCH,
                        message = "Не удалось получить ответ от AI. Возможно, ваш запрос не относится к теме животных. Пожалуйста, задайте вопрос о животных, их видах, питании, среде обитания или продолжительности жизни."
                    )
                )
                return ChatResponseResult(
                    response = errorResponse,
                    llmRequestJson = completionResult.requestJson,
                    llmResponseJson = completionResult.responseJson
                )
            }
            
            // Парсим JSON из строки ответа
            try {
                val parsed = json.decodeFromString<AnimalEncyclopediaResponse>(content)
                // Явная проверка типа для использования подклассов sealed class
                val finalResponse = when (parsed) {
                    is AnimalEncyclopediaResponse.Success -> parsed
                    is AnimalEncyclopediaResponse.Error -> parsed
                }
                return ChatResponseResult(
                    response = finalResponse,
                    llmRequestJson = completionResult.requestJson,
                    llmResponseJson = completionResult.responseJson
                )
            } catch (e: Exception) {
                logger.error("Ошибка парсинга JSON. Ответ от AI (первые 500 символов): ${content.take(500)}", e)
                // Если не удалось распарсить JSON, возвращаем ошибку валидации темы как fallback
                val errorResponse = AnimalEncyclopediaResponse.Error(
                    error = TopicValidationError(
                        errorCode = TopicValidationErrorCode.TOPIC_MISMATCH,
                        message = "Не удалось обработать ответ от AI. Возможно, ваш запрос не относится к теме животных. Пожалуйста, задайте вопрос о животных, их видах, питании, среде обитания или продолжительности жизни."
                    )
                )
                return ChatResponseResult(
                    response = errorResponse,
                    llmRequestJson = completionResult.requestJson,
                    llmResponseJson = completionResult.responseJson
                )
            }
        } catch (e: AIServiceException) {
            throw e
        } catch (e: Exception) {
            logger.error("Неожиданная ошибка при получении ответа от AI", e)
            throw AIServiceException(
                "Ошибка при получении структурированного ответа от AI: ${e.message}",
                e
            )
        }
    }
}
