package com.prike.data.repository

import com.prike.data.client.OpenAIClient
import com.prike.domain.entity.LLMCompletionMeta
import com.prike.domain.entity.LLMCompletionResult
import com.prike.domain.exception.AIServiceException
import com.prike.domain.repository.AIRepository
import com.prike.domain.repository.ModelInvocationRequest

class AIRepositoryImpl(
    private val aiClient: OpenAIClient
) : AIRepository {

    override suspend fun getCompletion(
        request: ModelInvocationRequest
    ): LLMCompletionResult {
        return try {
            val startedAt = System.currentTimeMillis()
            val completionResult = aiClient.getCompletion(
                userMessage = request.prompt,
                apiUrlOverride = request.endpoint,
                modelOverride = request.modelId,
                temperatureOverride = request.temperature,
                maxTokensOverride = request.maxTokens,
                responseFormatOverride = request.responseFormat,
                systemPromptOverride = request.systemPrompt,
                additionalParams = request.additionalParams
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
        } catch (exception: AIServiceException) {
            throw exception
        } catch (exception: Exception) {
            throw AIServiceException(
                "Ошибка при получении ответа от AI: ${exception.message}",
                exception
            )
        }
    }
}
