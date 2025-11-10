package com.prike.data.repository

import com.prike.data.client.OpenAIClient
import com.prike.domain.entity.LLMCompletionMeta
import com.prike.domain.entity.LLMCompletionResult
import com.prike.domain.exception.AIServiceException
import com.prike.domain.repository.AIRepository
import com.prike.domain.repository.AIResponseFormat
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Реализация репозитория AI.
 */
class AIRepositoryImpl(
    private val aiClient: OpenAIClient
) : AIRepository {

    private val jsonFormat by lazy {
        buildJsonObject {
            put("type", "json_object")
        }
    }

    override suspend fun getCompletion(
        prompt: String,
        temperature: Double,
        responseFormat: AIResponseFormat
    ): LLMCompletionResult {
        return try {
            val formatOverride = when (responseFormat) {
                AIResponseFormat.TEXT -> null
                AIResponseFormat.JSON_OBJECT -> jsonFormat
            }

            val startedAt = System.currentTimeMillis()
            val completionResult = aiClient.getCompletion(
                userMessage = prompt,
                temperatureOverride = temperature,
                responseFormatOverride = formatOverride
            )
            val duration = System.currentTimeMillis() - startedAt

            val content = completionResult.response.choices
                .firstOrNull()
                ?.message
                ?.content
                ?.trim()
                ?: throw AIServiceException("Пустой ответ от AI API (choices пусты)")

            LLMCompletionResult(
                content = content,
                meta = LLMCompletionMeta(
                    durationMs = duration,
                    promptTokens = completionResult.response.usage?.promptTokens,
                    completionTokens = completionResult.response.usage?.completionTokens,
                    totalTokens = completionResult.response.usage?.totalTokens,
                    requestJson = completionResult.requestJson,
                    responseJson = completionResult.responseJson
                )
            )
        } catch (e: AIServiceException) {
            throw e
        } catch (e: Exception) {
            throw AIServiceException(
                "Ошибка при получении ответа от AI: ${e.message}",
                e
            )
        }
    }
}
